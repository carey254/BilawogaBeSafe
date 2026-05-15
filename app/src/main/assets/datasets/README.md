# GBV Detection Training Datasets

## Dataset Structure

### CSV Format Requirements:
```csv
audio_file_path,label,severity,context,timestamp,duration_seconds
audio/emergency/scream_001.wav,emergency,high,domestic_violence,2024-01-01,10.5
audio/normal/conversation_001.wav,normal,low,family_discussion,2024-01-01,15.2
```

### Label Categories:
- **emergency**: screaming, help calls, threats, distress
- **normal**: conversations, casual talk, non-emergency
- **uncertain**: ambiguous situations needing review

### Severity Levels:
- **high**: immediate danger, violence, threats
- **medium**: distress, fear, coercion
- **low**: concern, worry, discomfort

### Context Types:
- domestic_violence, assault, harassment, family_disagreement, casual_conversation, etc.

## Files:
- `gbv_training_data.csv` - Main training dataset
- `emergency_samples.csv` - Emergency-only samples
- `non_emergency_samples.csv` - Normal interactions
- `dataset_metadata.json` - Dataset statistics and info
