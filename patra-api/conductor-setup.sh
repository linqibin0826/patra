#!/bin/zsh
set -e

echo "🚀 Starting Papertrace workspace setup..."

# Set default paths if not running in Conductor
if [ -z "$CONDUCTOR_ROOT_PATH" ]; then
    CONDUCTOR_ROOT_PATH="/Users/linqibin/Desktop/Papertrace-api"
    echo "⚠️  CONDUCTOR_ROOT_PATH not set, using: $CONDUCTOR_ROOT_PATH"
fi

if [ -z "$CONDUCTOR_WORKSPACE_PATH" ]; then
    CONDUCTOR_WORKSPACE_PATH="$(pwd)"
    echo "⚠️  CONDUCTOR_WORKSPACE_PATH not set, using: $CONDUCTOR_WORKSPACE_PATH"
fi

# Check prerequisites
echo ""
echo "📋 Checking prerequisites..."

# Check Java 21
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed"
    echo "Please install JDK 21 before running this setup"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "❌ Error: Java 21 or higher is required (found Java $JAVA_VERSION)"
    echo "Please install JDK 21 before running this setup"
    exit 1
fi
echo "✅ Java $JAVA_VERSION detected"

# Check Maven (use mvnw wrapper, so Maven installation is optional)
if [ ! -f "$CONDUCTOR_ROOT_PATH/mvnw" ]; then
    echo "❌ Error: Maven wrapper (mvnw) not found in $CONDUCTOR_ROOT_PATH"
    exit 1
fi
echo "✅ Maven wrapper found"

# Check Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Error: Docker is not installed"
    echo "Please install Docker to run infrastructure services"
    exit 1
fi
echo "✅ Docker detected"

# Check Docker Compose
if ! docker compose version &> /dev/null; then
    echo "❌ Error: Docker Compose is not available"
    echo "Please ensure Docker Compose is installed"
    exit 1
fi
echo "✅ Docker Compose detected"

# Copy .env file for Docker infrastructure
echo ""
echo "📝 Setting up environment configuration..."
if [ -f "$CONDUCTOR_ROOT_PATH/docker/.env.dev" ]; then
    cp "$CONDUCTOR_ROOT_PATH/docker/.env.dev" "$CONDUCTOR_WORKSPACE_PATH/.env"
    echo "✅ Copied docker/.env.dev to workspace"
else
    echo "⚠️  Warning: docker/.env.dev not found, skipping .env copy"
fi

# Check if Docker infrastructure is running
echo ""
echo "🔍 Checking Docker infrastructure status..."
cd "$CONDUCTOR_ROOT_PATH/docker/compose"

REQUIRED_SERVICES=("patra-mysql" "patra-redis" "patra-nacos" "patra-es" "patra-skywalking-oap")
ALL_RUNNING=true

for service in "${REQUIRED_SERVICES[@]}"; do
    if ! docker ps --format '{{.Names}}' | grep -q "^${service}$"; then
        ALL_RUNNING=false
        echo "⚠️  Service $service is not running"
    fi
done

if [ "$ALL_RUNNING" = false ]; then
    echo ""
    echo "⚠️  Some infrastructure services are not running"
    echo "To start all services, run:"
    echo "  cd $CONDUCTOR_ROOT_PATH/docker/compose"
    echo "  docker compose -f docker-compose.dev.yaml up -d"
    echo ""
    echo "Note: First-time startup may take several minutes for health checks"
else
    echo "✅ All required infrastructure services are running"
fi

# Install Maven dependencies and compile
echo ""
echo "📦 Installing Maven dependencies and compiling..."
cd "$CONDUCTOR_WORKSPACE_PATH"

# Make mvnw executable
chmod +x "$CONDUCTOR_ROOT_PATH/mvnw"

echo "Running: mvnw -q -DskipTests compile"
echo "⏳ This may take several minutes on first run..."

if "$CONDUCTOR_ROOT_PATH/mvnw" -q -DskipTests compile; then
    echo "✅ Maven dependencies installed and project compiled successfully"
else
    echo "❌ Error: Maven compilation failed"
    echo "Please check the output above for details"
    exit 1
fi

# Run tests to verify everything works
echo ""
echo "🧪 Running tests to verify setup..."
echo "Running: mvnw test"
echo "⏳ This may take a few minutes..."
echo ""

if "$CONDUCTOR_ROOT_PATH/mvnw" test; then
    echo ""
    echo "✅ All tests passed!"
else
    echo ""
    echo "❌ Warning: Some tests failed"
    echo "The workspace is set up, but you may need to:"
    echo "  • Check that Docker infrastructure services are healthy"
    echo "  • Verify Nacos configuration is correct"
    echo "  • Review test output above for specific errors"
    echo ""
    echo "You can re-run tests anytime with: ./conductor-test.sh"
    exit 1
fi

echo ""
echo "✅ Workspace setup completed successfully!"
echo ""
echo "📚 Next steps:"
echo "  • Click the 'Run' button to run tests anytime"
echo "  • Ensure Docker infrastructure is running (see above)"
echo "  • Configure Nacos settings if needed (http://localhost:8848)"
echo "  • Run individual microservices from their *-boot modules"
echo "  • Check README.md for detailed development guide"
echo ""
