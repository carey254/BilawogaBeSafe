import { Audio } from 'expo-av';

let emergencySound = null;

export const loadEmergencySound = async () => {
  try {
    const { sound } = await Audio.Sound.createAsync(
      require('../assets/sounds/emergency_alert.mp3'),
      { shouldPlay: false }
    );
    emergencySound = sound;
  } catch (error) {
    console.error('Error loading emergency sound:', error);
  }
};

export const playEmergencySound = async () => {
  try {
    if (emergencySound) {
      await emergencySound.setPositionAsync(0);
      await emergencySound.playAsync();
    }
  } catch (error) {
    console.error('Error playing emergency sound:', error);
  }
};

export const stopEmergencySound = async () => {
  try {
    if (emergencySound) {
      await emergencySound.stopAsync();
    }
  } catch (error) {
    console.error('Error stopping emergency sound:', error);
  }
};

export const unloadEmergencySound = async () => {
  try {
    if (emergencySound) {
      await emergencySound.unloadAsync();
    }
  } catch (error) {
    console.error('Error unloading emergency sound:', error);
  }
}; 