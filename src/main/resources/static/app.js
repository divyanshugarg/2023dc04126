class RAGAChat {
    constructor() {
        this.threadId = null;
        this.apiBaseUrl = '/api/conversation';
        this.isLoading = false;
        this.initializeEventListeners();
    }

    initializeEventListeners() {
        const sendBtn = document.getElementById('sendBtn');
        const messageInput = document.getElementById('messageInput');
        const newConversationBtn = document.getElementById('newConversationBtn');

        // Initially disable send button
        this.updateSendButtonState();

        sendBtn.addEventListener('click', () => this.sendMessage());
        
        // Update send button state on input
        messageInput.addEventListener('input', () => this.updateSendButtonState());
        
        messageInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                if (!sendBtn.disabled) {
                    this.sendMessage();
                }
            }
        });

        newConversationBtn.addEventListener('click', () => this.showNewConversationModal());

        // Modal event listeners
        const modalOverlay = document.getElementById('modalOverlay');
        const modalCancel = document.getElementById('modalCancel');
        const modalNo = document.getElementById('modalNo');
        const modalYes = document.getElementById('modalYes');

        modalCancel.addEventListener('click', () => this.hideModal());
        modalNo.addEventListener('click', () => this.startNewConversation(false));
        modalYes.addEventListener('click', () => this.startNewConversation(true));
        
        // Close modal when clicking outside
        modalOverlay.addEventListener('click', (e) => {
            if (e.target === modalOverlay) {
                this.hideModal();
            }
        });

        // Sample query click handlers - use event delegation for dynamic elements
        const sampleQueriesContainer = document.getElementById('sampleQueries');
        if (sampleQueriesContainer) {
            sampleQueriesContainer.addEventListener('click', (e) => {
                const queryItem = e.target.closest('.query-item');
                if (queryItem) {
                    const query = queryItem.getAttribute('data-query');
                    if (query) {
                        messageInput.value = query;
                        this.updateSendButtonState();
                        // Focus on the input field so user can edit if needed
                        messageInput.focus();
                        // Move cursor to end of text
                        messageInput.setSelectionRange(query.length, query.length);
                        // Hide sample queries after selecting one
                        this.hideSampleQueries();
                    }
                }
            });
        }
    }

    showNewConversationModal() {
        const modalOverlay = document.getElementById('modalOverlay');
        // Only show modal if there's an active thread
        if (this.threadId) {
            modalOverlay.classList.add('show');
        } else {
            // No active thread, just start new conversation without asking
            this.startNewConversation(false);
        }
    }

    hideModal() {
        const modalOverlay = document.getElementById('modalOverlay');
        modalOverlay.classList.remove('show');
    }

    async sendMessage() {
        const messageInput = document.getElementById('messageInput');
        const message = messageInput.value.trim();

        if (!message) {
            return;
        }

        // Add user message to UI
        this.addMessage('user', message);
        messageInput.value = '';
        this.setLoading(true);

        try {
            const response = await fetch(`${this.apiBaseUrl}/chat`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    message: message,
                    threadId: this.threadId
                })
            });

            const data = await response.json();

            if (data.success) {
                // Thread ID will be set on first message
                if (data.threadId) {
                    this.threadId = data.threadId;
                    this.updateStatus(`Turn ${data.turnCount} | Thread: ${data.threadId.substring(0, 8)}...`);
                } else {
                    this.updateStatus(`Turn ${data.turnCount}`);
                }
                this.addMessage('assistant', data.response);
            } else {
                this.addMessage('assistant', `Error: ${data.errorMessage || 'An error occurred'}`);
                this.updateStatus('Error occurred', 'error');
            }
        } catch (error) {
            console.error('Error sending message:', error);
            this.addMessage('assistant', 'Sorry, I encountered an error. Please try again.');
            this.updateStatus('Connection error', 'error');
        } finally {
            this.setLoading(false);
        }
    }

    updateSendButtonState() {
        const sendBtn = document.getElementById('sendBtn');
        const messageInput = document.getElementById('messageInput');
        const hasText = messageInput.value.trim().length > 0;
        
        // Only disable if not currently loading
        if (!this.isLoading) {
            sendBtn.disabled = !hasText;
        }
    }

    async startNewConversation(deleteThread) {
        this.hideModal();
        this.setLoading(true);
        this.updateStatus('Starting new conversation...', 'loading');

        try {
            const response = await fetch(`${this.apiBaseUrl}/new`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    deleteCurrentThread: deleteThread
                })
            });

            const data = await response.json();

            if (data.success) {
                // Reset threadId - will be created on first message
                this.threadId = null;
                
                // Clear messages
                const messagesContainer = document.getElementById('messages');
                messagesContainer.innerHTML = '';
                
                // Show sample queries again
                this.showSampleQueries();
                
                // Don't add welcome message - removed as per requirement
                this.updateStatus('New conversation ready - send your first message to start!');
            } else {
                this.updateStatus(`Error: ${data.errorMessage}`, 'error');
            }
        } catch (error) {
            console.error('Error starting new conversation:', error);
            this.updateStatus('Failed to start new conversation', 'error');
        } finally {
            this.setLoading(false);
        }
    }

    addMessage(role, content) {
        const messagesContainer = document.getElementById('messages');
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${role}-message`;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        
        const roleLabel = role === 'user' ? 'You' : 'CouTalk';
        contentDiv.innerHTML = `<strong>${roleLabel}:</strong> ${this.formatMessage(content)}`;

        messageDiv.appendChild(contentDiv);
        messagesContainer.appendChild(messageDiv);

        // Hide sample queries after first message
        this.hideSampleQueries();

        // Scroll to bottom
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    hideSampleQueries() {
        const sampleQueries = document.getElementById('sampleQueries');
        if (sampleQueries) {
            sampleQueries.classList.add('hidden');
        }
    }

    showSampleQueries() {
        const sampleQueries = document.getElementById('sampleQueries');
        const messagesContainer = document.getElementById('messages');
        // Only show if there are no messages
        if (sampleQueries && messagesContainer.children.length === 0) {
            sampleQueries.classList.remove('hidden');
        }
    }

    formatMessage(content) {
        // Basic formatting: preserve line breaks and format code blocks
        return content
            .replace(/\n/g, '<br>')
            .replace(/`([^`]+)`/g, '<code>$1</code>')
            .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
            .replace(/\*([^*]+)\*/g, '<em>$1</em>');
    }

    setLoading(loading) {
        const sendBtn = document.getElementById('sendBtn');
        const messageInput = document.getElementById('messageInput');
        
        this.isLoading = loading;
        sendBtn.disabled = loading;
        messageInput.disabled = loading;

        if (loading) {
            sendBtn.textContent = 'Sending...';
        } else {
            sendBtn.textContent = 'Send';
            // Update button state based on input after loading completes
            this.updateSendButtonState();
        }
    }

    updateStatus(message, type = '') {
        const statusDiv = document.getElementById('status');
        statusDiv.textContent = message;
        statusDiv.className = `status ${type}`;
    }
}

// Initialize chat when page loads
document.addEventListener('DOMContentLoaded', () => {
    new RAGAChat();
});
