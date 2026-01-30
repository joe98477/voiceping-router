FROM node:20-bullseye-slim

WORKDIR /app

COPY package.json ./
COPY prisma ./prisma
RUN apt-get update && apt-get install -y openssl
RUN npm install --production
RUN npx prisma generate

COPY src ./src

EXPOSE 4000

CMD ["sh", "-c", "npm run prisma:migrate && npm start"]
