import sqlalchemy
from flask import Flask, jsonify, request
import requests
import os
import random

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

def getads():
    with engine.begin() as conn:
        result = conn.execute(sqlalchemy.text('SELECT * FROM ads'))
        ads = [dict(row) for row in result.mappings().all()]
        app.logger.info("Ads retrieved from the database: {}".format(ads))
        return ads


@app.route('/ads', methods=['GET'])
def ads():
    app.logger.info('ads request received!')
    return jsonify(getads())

def main():
    global engine
    PORT = int(os.getenv('PORT', '8080'))
    listener = None

    engine = create_engine("mysql+pymysql://adsuser:adspass@mysql.mysql:3306/adsdb")
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