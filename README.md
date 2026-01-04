# RAGA Agent - Spring Boot Microservice

RAGA (Request and Generate Agent) is a Java Spring Boot microservice that provides a conversational interface for developers to request synthetic test data using natural language. It integrates with OpenAI's GPT-4o-mini model via API to enable multi-turn conversations with dialogue state management, small talk handling, and out-of-context inquiry management.

## Features

- 🤖 **Conversational AI**: Multi-turn conversation support with RAGA agent
- 🔒 **Safety Filters**: Built-in sanitization and jailbreak detection
- 📊 **Dialogue State Management**: Maintains conversation context across turns
- 🗄️ **Vector Store Integration**: Uses OpenAI's vector store for file storage
- 🎨 **Single Page Application**: Modern, responsive web UI
- 🔐 **Secure Configuration**: Properties-based secret management

## Prerequisites

- Java 17 or higher
- Gradle 9.0 or higher
- OpenAI API key with access to Assistants API (v2)
- OpenAI account with access to GPT-4o-mini model (formerly referred to as GPT-4.1 nano)

## Quick Start

1. **Configure API Key**: Ensure your OpenAI API key is in the `open_ai_key` file or set `OPENAI_API_KEY` environment variable
2. **Build**: `./gradlew build`
3. **Run**: `./gradlew bootRun`
4. **Access**: Open `http://localhost:8080` in your browser

## Setup Instructions

### 1. Configure OpenAI API Key

The application reads the OpenAI API key in the following order of priority:

1. **Environment Variable** (highest priority):
   ```bash
   export OPENAI_API_KEY=sk-your-api-key-here
   ```

2. **Application Properties**:
   Edit `src/main/resources/application.properties` and set:
   ```properties
   openai.api.key=sk-your-api-key-here
   ```

3. **File in Project Root** (lowest priority):
   The `open_ai_key` file in the project root should contain just your API key:
   ```bash
   # The file should contain just your API key (no quotes, no spaces)
   sk-your-api-key-here
   ```

### 2. Build the Project

```bash
./gradlew build
```

### 3. Run the Application

```bash
./gradlew bootRun
```

Or using the JAR:

```bash
java -jar build/libs/raga-agent-service-1.0.0.jar
```

The application will start on `http://localhost:8080`

### 4. Access the Application

Open your browser and navigate to:
```
http://localhost:8080
```

## Project Structure

```
.
├── build.gradle                 # Gradle build configuration
├── settings.gradle              # Gradle settings
├── src/
│   ├── main/
│   │   ├── java/com/raga/
│   │   │   ├── RagaAgentApplication.java    # Main Spring Boot application
│   │   │   ├── config/                      # Configuration classes
│   │   │   │   ├── OpenAIConfig.java
│   │   │   │   ├── SafetyConfig.java
│   │   │   │   └── WebConfig.java
│   │   │   ├── controller/                  # REST controllers
│   │   │   │   └── ConversationController.java
│   │   │   ├── dto/                         # Data Transfer Objects
│   │   │   │   ├── ConversationRequest.java
│   │   │   │   └── ConversationResponse.java
│   │   │   └── service/                     # Business logic services
│   │   │       ├── OpenAIService.java
│   │   │       ├── SafetyFilterService.java
│   │   │       └── DialogueStateService.java
│   │   └── resources/
│   │       ├── application.properties        # Application configuration
│   │       └── static/                     # Frontend files
│   │           ├── index.html
│   │           ├── styles.css
│   │           └── app.js
└── README.md
```

## API Endpoints

### POST `/api/conversation/chat`
Send a message to the RAGA agent.

**Request Body:**
```json
{
  "message": "Generate 100 user records with name, email, and age",
  "threadId": "thread_abc123" // Optional
}
```

**Response:**
```json
{
  "threadId": "thread_abc123",
  "response": "I'll generate 100 user records...",
  "success": true,
  "turnCount": 1
}
```

### POST `/api/conversation/new`
Start a new conversation.

**Response:**
```json
{
  "threadId": "thread_xyz789",
  "response": "New conversation started...",
  "success": true,
  "turnCount": 0
}
```

### GET `/api/conversation/status/{threadId}`
Get the status of a conversation thread.

## Configuration

Configuration is managed through `src/main/resources/application.properties`:

```properties
# OpenAI Configuration
openai.api.key=${OPENAI_API_KEY:}
openai.api.base-url=https://api.openai.com/v1
openai.model=gpt-4o-mini
openai.assistant.name=RAGA Agent
openai.assistant.instructions=...

# Safety Configuration
safety.filter.enabled=true
safety.jailbreak.detection.enabled=true
safety.domain.validation.enabled=true
```

## Safety Features

The application includes several safety mechanisms:

1. **Input Sanitization**: Removes control characters and normalizes input
2. **Jailbreak Detection**: Identifies attempts to override system instructions
3. **Domain Validation**: Ensures requests are relevant to testing domain
4. **Small Talk Handling**: Allows natural conversation while maintaining focus

## OpenAI Integration

The service uses OpenAI's Assistants API (v2) with the following features:

- **Assistant Creation**: Creates a specialized RAGA assistant on first use
- **Vector Store**: Optional vector store for file-based knowledge
- **Thread Management**: Maintains conversation threads for multi-turn dialogues
- **Run Polling**: Monitors assistant runs until completion

## Development

### Running Tests

```bash
./gradlew test
```

### Building for Production

```bash
./gradlew clean build
```

## Troubleshooting

### API Key Issues
- Ensure the `open_ai_key` file exists and contains a valid API key
- Check that the API key has access to the Assistants API
- Verify the key is not expired

### Connection Issues
- Check your internet connection
- Verify OpenAI API is accessible from your network
- Review application logs for detailed error messages

### Assistant Creation Failures
- Ensure your OpenAI account has access to the Assistants API
- Check API rate limits
- Verify the model name is correct (gpt-4o-mini)

## License

This project is part of a BITS WILP assignment.

## Author

2023DC04126

