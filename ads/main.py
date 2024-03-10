from flask import Flask, jsonify, request
import mysql.connector
import requests
import os
import random

app = Flask(__name__)

# Database configuration
db_config = {
    'host': 'mysql.mysql',
    'user': 'adsuser',
    'password': 'adspass',
    'database': 'adsdb'
}

def getads():
    try:
        connection = mysql.connector.connect(**db_config)
        cursor = connection.cursor(dictionary=True)
        cursor.execute('SELECT * FROM ads')
        ads = cursor.fetchall()
        cursor.close()
        connection.close()
        app.logger.info("ads retrieved from the database: {}".format(ads))
        return ads
    except mysql.connector.Error as error:
        app.logger.info("Failed to retrieve ads from MySQL table {}".format(error))
        return []

@app.route('/ads', methods=['GET'])
def ads():
    app.logger.info('ads request received!')
    return jsonify(getads())

if __name__ == '__main__':
    PORT = int(os.getenv('PORT', '8080'))
    app.run(debug=True, host='0.0.0.0', port=PORT)