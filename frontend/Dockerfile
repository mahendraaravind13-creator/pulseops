# Stage 1: build the static bundle
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Empty by default so the browser calls /api on the same origin and nginx proxies it.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# Stage 2: serve it with nginx
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
