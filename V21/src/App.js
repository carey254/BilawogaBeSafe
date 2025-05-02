import React, { useState } from "react"; 
import WelcomeScreen from "./components/WelcomeScreen";
import Chatbot from "./components/Chatbot";
import "./App.css";

const App = () => {
  // State to track whether the chatbot conversation has started
  const [isChatStarted, setIsChatStarted] = useState(false);

  // Handler to start the chat
  const handleStartChat = () => {
    setIsChatStarted(true);
  };

  return (
    <div className="app">
      {isChatStarted ? (
        // Render the Chatbot component once chat starts
        <Chatbot />
      ) : (
        // Render the WelcomeScreen component before starting the chat
        <WelcomeScreen onStart={handleStartChat} />
      )}
    </div>
  );
};

export default App;
