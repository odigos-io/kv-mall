FROM node:18.3.0-alpine3.14
WORKDIR /app
COPY package.json /app
COPY package-lock.json /app
RUN npm ci
COPY . /app
USER 15000
CMD ["node","app.js"]