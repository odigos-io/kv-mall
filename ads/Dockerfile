FROM python:3.8-slim

WORKDIR /app
ADD . /app
RUN pip install --no-cache-dir -r requirements.txt
EXPOSE 8080
ENV FLASK_ENV development

CMD ["python", "main.py"]