# BaseManager Makefile
# Load test server directory from .local.env

include .local.env

.PHONY: build deploy deploy-clean clean test verify

# Build the plugin
build:
	./mvnw clean package -DskipTests

# Build and deploy to test server (keeps configs)
deploy: build
	@rm -f "$(TEST_SERVER_DIR)/plugins/"basemanager*.jar
	@rm -rf "$(TEST_SERVER_DIR)/plugins/.paper-remapped/"*[Bb]ase[Mm]anager*
	@cp target/basemanager-*.jar "$(TEST_SERVER_DIR)/plugins/"
	@echo "Deployed to $(TEST_SERVER_DIR)/plugins/"

# Build and deploy with fresh configs (keeps database!)
deploy-clean: build
	@rm -f "$(TEST_SERVER_DIR)/plugins/"basemanager*.jar
	@rm -rf "$(TEST_SERVER_DIR)/plugins/.paper-remapped/"*[Bb]ase[Mm]anager*
	@rm -f "$(TEST_SERVER_DIR)/plugins/BaseManager/"*.yml
	@cp target/basemanager-*.jar "$(TEST_SERVER_DIR)/plugins/"
	@echo "Deployed (clean configs) to $(TEST_SERVER_DIR)/plugins/"

# Run tests
test:
	./mvnw test

# Full verification (compile + test + lint + spotbugs)
verify:
	./mvnw clean verify

# Clean build artifacts
clean:
	./mvnw clean
