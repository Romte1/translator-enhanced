FROM node:20-alpine

WORKDIR /app
COPY server.mjs .

ENV PORT=8080
ENV DATA_DIR=/data

VOLUME /data
EXPOSE 8080

CMD ["node", "server.mjs"]
