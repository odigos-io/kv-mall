import sqlalchemy
from flask import Flask, jsonify, request
import requests
import os
import random

from sqlalchemy import create_engine, event
from google.cloud.sqlcommenter.sqlalchemy.executor import BeforeExecuteFactory

app = Flask(__name__)
engine = None

# Database configuration
db_config = {
    'host': 'mysql.mysql',
    'user': 'adsuser',
    'password': 'adspass',
    'database': 'adsdb'
}

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
    try:
        engine = create_engine("mysql+pymysql://adsuser:adspass@mysql.mysql:3306/adsdb")
        event.listen(engine, 'before_cursor_execute', BeforeExecuteFactory(with_opentelemetry=True), retval=True)

        app.run(host='0.0.0.0', port=PORT, debug=True)
    except Exception as e:
        print('Encountered exception %s'%(e))

if __name__ == '__main__':
    main()