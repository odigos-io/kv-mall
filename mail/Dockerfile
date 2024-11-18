FROM node:22 AS builder
WORKDIR /app
COPY package.json /app
COPY yarn.lock /app
RUN yarn install
COPY . /app
RUN yarn build

FROM node:22-alpine3.20
WORKDIR /app
COPY --from=builder /app/package.json /app
COPY --from=builder /app/yarn.lock /app
RUN yarn install --production
COPY --from=builder /app/dist /app/dist

USER 15000
CMD ["node","/app/dist/index.js"]
