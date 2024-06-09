FROM node:20.13.1
WORKDIR /usr/src/app
COPY package*.json ./
RUN npm install
COPY . .
EXPOSE 8080
RUN chown -R node /usr/src/app
USER node
CMD [ "npm", "run", "start"]
