import React from "react";
import "./MessageBubble.css";

const MessageBubble = ({ message }) => {
  const isUser = message.user === "User";

  return (
    <div className={`message-bubble ${isUser ? "user" : "bot"}`}>
      <p>{message.text}</p>
    </div>
  );
};

export default MessageBubble;
