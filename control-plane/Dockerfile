FROM node:20-alpine

WORKDIR /app

COPY package.json ./
COPY prisma ./prisma
RUN npm install --production
RUN npx prisma generate

COPY src ./src

EXPOSE 4000

CMD ["sh", "-c", "npm run prisma:migrate && npm start"]
