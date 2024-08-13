import sqlalchemy
from flask import Flask, jsonify, request
import signal
import os
import sys
import time

from sqlalchemy import create_engine, event
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

def signal_handler(sig, frame):
    print('Terminating inventory service')
    sys.exit(0)

def getads():
    while True:
        try:
            with engine.begin() as conn:
                # If OpenTelemetry exists, create new span
                if propagator is not None:
                    import opentelemetry.trace as trace
                    tracer = trace.get_tracer(instrumenting_module_name="database/sql")
                    with tracer.start_as_current_span("SELECT * FROM ads") as span:
                        span.set_attribute("db.type", "mysql")
                        span.set_attribute("db.instance", "adsdb")
                        span.set_attribute("db.statement", "SELECT * FROM ads'")
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

def main():
    signal.signal(signal.SIGTERM, signal_handler)
    global engine
    PORT = int(os.getenv('PORT', '8080'))
    listener = None

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