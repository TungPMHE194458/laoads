# ===== Build stage =====
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json .npmrc ./
# .npmrc bật legacy-peer-deps (openapi-typescript khai peer TS5, dự án dùng TS6 — giống BOC)
RUN npm ci --no-audit --no-fund

COPY . .

# Biến VITE_* được nhúng lúc build.
#   VITE_API_MODE: real (mặc định cho môi trường thật) | hybrid (demo, mock phần BE chưa có) | mock
#   VITE_API_BASE_URL: bỏ trống → /api/v1 cùng origin (nginx proxy bên dưới)
ARG VITE_API_MODE=real
ARG VITE_API_BASE_URL=
ENV VITE_API_MODE=${VITE_API_MODE}
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

# ===== Runtime stage — nginx phục vụ static =====
FROM nginx:1.27-alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost/ || exit 1
