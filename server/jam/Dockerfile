FROM golang:1.27-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/jam ./cmd/jam

FROM alpine:3.22
RUN addgroup -S jam && adduser -S -G jam -H -u 10001 jam
WORKDIR /app
COPY --from=build /out/jam /app/jam
USER jam
ENV PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD wget -qO- "http://127.0.0.1:${PORT}/healthz" >/dev/null || exit 1
CMD ["/app/jam"]
