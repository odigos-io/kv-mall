import threading
import sqlalchemy
from flask import Flask, jsonify, request
import signal
import os
import sys
import time
import pymysql
import urllib.parse  # For escaping credentials



app = Flask(__name__)
engine = None
lock_thread_started = False
lock_thread_lock = threading.Lock()

db_user = os.environ.get("DB_USER", "root")
db_pass = urllib.parse.quote_plus(os.environ.get("DB_PASS", ""))
db_host = os.environ.get("DB_HOST", "localhost")
db_name = os.environ.get("DB_NAME", "")

def signal_handler(sig, frame):
    print('Terminating inventory service')
    sys.exit(0)

def getads():
    while True:
        try:
            with engine.connect() as conn:
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


def single_ads_table_lock(lock_duration: int):
    try:
        conn = pymysql.connect(
            db_host,
            db_user,
            db_pass,
            db_name,
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

    db_url = f"mysql+pymysql://{db_user}:{db_pass}@{db_host}:3306/{db_name}"
    engine = create_engine(db_url)
    app.logger.info("Not using OpenTelemetry")
    listener = BeforeExecuteFactory()
  
    event.listen(engine, 'before_cursor_execute', listener, retval=True)
    app.run(host='0.0.0.0', port=PORT, debug=False)


if __name__ == '__main__':
    main()