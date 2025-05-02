import React from "react";
import "./WelcomeScreen.css";

const WelcomeScreen = ({ onStart }) => {
  return (
    <div className="welcome-screen">
      <h1>Welcome to Your Mental Health Chatbot</h1>
      <p>This is a safe space to share your thoughts. Let’s start the journey to healing together.</p>
      <button onClick={onStart}>Start Chat</button>
    </div>
  );
};

export default WelcomeScreen;
