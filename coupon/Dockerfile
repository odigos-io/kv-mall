FROM node:18.3.0-alpine3.14
COPY . /app
WORKDIR /app
RUN npm install
CMD ["node","--require","/app/instrumentation.js","app.js"]