import os
import sys
import time
import signal
import threading
import urllib.parse

from flask import Flask, jsonify, request
from sqlalchemy import create_engine, event, text
from google.cloud.sqlcommenter.sqlalchemy.executor import BeforeExecuteFactory

try:
    from opentelemetry.trace.propagation.tracecontext import (
        TraceContextTextMapPropagator,
    )

    propagator = TraceContextTextMapPropagator()
except ImportError:
    propagator = None

app = Flask(__name__)
engine = None

# Database configuration from env
db_user = os.environ.get("DB_USER", "root")
db_pass = urllib.parse.quote_plus(os.environ.get("DB_PASS", ""))
db_host = os.environ.get("DB_HOST", "localhost")
db_name = os.environ.get("DB_NAME", "")
db_url = f"mysql+pymysql://{db_user}:{db_pass}@{db_host}:3306/{db_name}"

# Graceful shutdown
def signal_handler(sig, frame):
    print('Terminating inventory service')
    sys.exit(0)

# /ads endpoint
def getads():
    while True:
        try:
            with engine.begin() as conn:
                result = conn.execute(text('SELECT * FROM ads'))
                ads = [dict(row) for row in result.mappings().all()]
                app.logger.info(f"Ads retrieved from the database: {ads}")
                return ads
        except Exception as e:
            app.logger.error(f"Error retrieving ads from the database: {e}, retrying again soon")
            time.sleep(1)

@app.route('/ads', methods=['GET'])
def ads():
    app.logger.info('/ads request received!')
    return jsonify(getads())

def single_ads_table_lock(lock_duration: int):
    try:
        with engine.connect() as conn:
            app.logger.info(f"Locking 'ads' table for {lock_duration}s")
            conn.execute(text("LOCK TABLES ads WRITE"))
            time.sleep(lock_duration)
            app.logger.info("Lock released")
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

# App entry point
def main():
    global engine
    signal.signal(signal.SIGTERM, signal_handler)
    PORT = int(os.getenv('PORT', '8080'))

    engine = create_engine(db_url)

    if propagator is not None:
        app.logger.info("Using OpenTelemetry auto-instrumentation")
        listener = BeforeExecuteFactory(with_opentelemetry=True)
    else:
        app.logger.info("Running without OpenTelemetry")
        listener = BeforeExecuteFactory()

    event.listen(engine, 'before_cursor_execute', listener, retval=True)
    app.run(host='0.0.0.0', port=PORT, debug=False)

if __name__ == '__main__':
    main()
