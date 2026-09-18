.PHONY: setup dev-frontend dev-backend

setup:
	./scripts/bootstrap.sh

dev-frontend:
	cd frontend && npm run dev

dev-backend:
	cd backend && sh mvnw spring-boot:run
