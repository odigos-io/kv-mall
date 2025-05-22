import threading
import sqlalchemy
from flask import Flask, jsonify, request
import signal
import os
import sys
import time
import pymysql

from sqlalchemy import create_engine, event
from google.cloud.sqlcommenter.sqlalchemy.executor import BeforeExecuteFactory

# try:
#     from opentelemetry.trace.propagation.tracecontext import (
#         TraceContextTextMapPropagator,
#     )
#     propagator = TraceContextTextMapPropagator()
# except ImportError:
#     propagator = None

app = Flask(__name__)
engine = None
lock_thread_started = False
lock_thread_lock = threading.Lock()

def signal_handler(sig, frame):
    print('Terminating inventory service')
    sys.exit(0)

def getads():
    while True:
        try:
            with engine.connect() as conn:
                if propagator is not None:
                    # import opentelemetry.trace as trace
                    # tracer = trace.get_tracer(instrumenting_module_name="database/sql")
                    # with tracer.start_as_current_span("SELECT * FROM ads") as span:
                        # span.set_attribute("db.type", "mysql")
                        # span.set_attribute("db.instance", "adsdb")
                        # span.set_attribute("db.statement", "SELECT * FROM ads'")
                        result = conn.execute(sqlalchemy.text('SELECT * FROM ads'))
                        ads = [dict(row) for row in result.mappings().all()]
                        app.logger.info("Ads retrieved from the database: {}".format(ads))
                        return ads
                result = conn.execute(sqlalchemy.text('SELECT * FROM ads'))
                ads = [dict(row) for row in result.mappings().all()]
                app.logger.info("Ads retrieved from the database: {}".format(ads))
                return ads
        except Exception as e:
            app.logger.error("Error retrieving ads from the database: {}, retrying again soon".format(e))
            time.sleep(1)
            continue

@app.route('/ads', methods=['GET'])
def ads():
    app.logger.info('ads request received!')
    return jsonify(getads())

# def periodic_ads_table_lock(lock_duration: int, cooldown: int):
#     try:
#         conn = pymysql.connect(
#             host="mysql.kv-mall-infra",
#             user="adsuser",
#             password="adspass",
#             database="adsdb",
#             autocommit=False
#         )
#         cursor = conn.cursor()
#         while True:
#             try:
#                 app.logger.info(f"Locking 'ads' table for {lock_duration}s")
#                 cursor.execute("LOCK TABLES ads WRITE")
#                 cursor.execute("SHOW OPEN TABLES WHERE In_use > 0")

#                 time.sleep(lock_duration)
#                 cursor.execute("UNLOCK TABLES")
#                 app.logger.info("Lock released")
#                 time.sleep(cooldown)
#             except Exception as e:
#                 app.logger.error(f"Lock cycle error: {e}")
#                 time.sleep(5)
#     except Exception as e:
#         app.logger.error(f"Failed to establish locking connection: {e}")

def single_ads_table_lock(lock_duration: int):
    try:
        conn = pymysql.connect(
            host="mysql.kv-mall-infra",
            user="adsuser",
            password="adspass",
            database="adsdb",
            autocommit=False
        )
        cursor = conn.cursor()
        app.logger.info(f"Locking 'ads' table for {lock_duration}s")
        cursor.execute("LOCK TABLES ads WRITE")
        time.sleep(lock_duration)
        cursor.execute("UNLOCK TABLES")
        app.logger.info("Lock released")
        cursor.close()
        conn.close()
    except Exception as e:
        app.logger.error(f"Error while locking ads table: {e}")


@app.route('/simulate-lock', methods=['POST'])
def start_single_lock():
    try:
        data = request.get_json(force=True)
        lock_duration = int(data.get("lock_duration", 10))
    except Exception as e:
        app.logger.warning(f"Invalid input to /simulate-lock, defaulting to 10s: {e}")
        lock_duration = 10

    thread = threading.Thread(target=single_ads_table_lock, args=(lock_duration,))
    thread.daemon = True
    thread.start()

    app.logger.info(f"Started async lock thread for {lock_duration}s.")
    return jsonify({
        "message": f"Asynchronous lock started for {lock_duration} seconds"
    }), 202


def main():
    signal.signal(signal.SIGTERM, signal_handler)
    global engine
    PORT = int(os.getenv('PORT', '8080'))

    engine = create_engine("mysql+pymysql://adsuser:adspass@mysql.kv-mall-infra:3306/adsdb")
    if propagator is not None:
        app.logger.info("Using OpenTelemetry")
        listener = BeforeExecuteFactory(with_opentelemetry=True)
    else:
        app.logger.info("Not using OpenTelemetry")
        listener = BeforeExecuteFactory()
  
    event.listen(engine, 'before_cursor_execute', listener, retval=True)
    app.run(host='0.0.0.0', port=PORT, debug=False)


if __name__ == '__main__':
    main()