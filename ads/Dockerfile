FROM python:3.11

WORKDIR /app
COPY . /app

# Upgrade pip and install dependencies
RUN pip install --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

EXPOSE 8080

ENV FLASK_ENV=development

CMD ["python", "main.py"]