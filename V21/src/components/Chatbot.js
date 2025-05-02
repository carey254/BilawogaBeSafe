import React, { useState } from "react";
import MessageBubble from "./MessageBubble";
import "./Chatbot.css";

const Chatbot = () => {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");

  const handleSend = () => {
    if (input.trim()) {
      const newMessage = { text: input, user: "User" };
      setMessages([...messages, newMessage]);

      // Simulate chatbot response
      setTimeout(() => {
        const botMessage = { text: "Thank you for sharing. How can I assist further?", user: "Bot" };
        setMessages((prev) => [...prev, botMessage]);
      }, 1000);

      setInput("");
    }
  };

  return (
    <div className="chatbot">
      <div className="messages">
        {messages.map((msg, index) => (
          <MessageBubble key={index} message={msg} />
        ))}
      </div>
      <div className="input-area">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Type your message..."
        />
        <button onClick={handleSend}>Send</button>
      </div>
    </div>
  );
};

export default Chatbot;
