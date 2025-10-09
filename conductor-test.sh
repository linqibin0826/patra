#!/bin/zsh
set -e

echo "🧪 Running Papertrace test suite..."

# Set default paths if not running in Conductor
if [ -z "$CONDUCTOR_ROOT_PATH" ]; then
    CONDUCTOR_ROOT_PATH="/Users/linqibin/Desktop/Papertrace-api"
    echo "⚠️  CONDUCTOR_ROOT_PATH not set, using: $CONDUCTOR_ROOT_PATH"
fi

if [ -z "$CONDUCTOR_WORKSPACE_PATH" ]; then
    CONDUCTOR_WORKSPACE_PATH="$(pwd)"
    echo "⚠️  CONDUCTOR_WORKSPACE_PATH not set, using: $CONDUCTOR_WORKSPACE_PATH"
fi

cd "$CONDUCTOR_WORKSPACE_PATH"

# Check if mvnw exists
if [ ! -f "$CONDUCTOR_ROOT_PATH/mvnw" ]; then
    echo "❌ Error: Maven wrapper (mvnw) not found in $CONDUCTOR_ROOT_PATH"
    exit 1
fi

# Make mvnw executable
chmod +x "$CONDUCTOR_ROOT_PATH/mvnw"

echo ""
echo "Running: mvnw clean test"
echo "⏳ This may take a few minutes..."
echo ""

# Run all tests
if "$CONDUCTOR_ROOT_PATH/mvnw" clean test; then
    echo ""
    echo "✅ All tests passed successfully!"
    echo ""
else
    echo ""
    echo "❌ Some tests failed"
    echo "Please check the output above for details"
    exit 1
fi
