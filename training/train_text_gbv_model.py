"""
Train a Text-based GBV Detection TFLite model from gbv_dataset.csv.

This script:
1. Loads the 5,000-sample text conversation dataset
2. Extracts text features (keyword counts, TF-IDF-like features, sentiment indicators)
3. Trains a neural network for classification (abuse/distress/emergency/normal)
4. Converts to TFLite format for on-device inference
5. Outputs: gbv_text_model.tflite
"""

import csv
import os
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder

DATASET_PATH = os.path.join(os.path.dirname(__file__),
    '..', 'app', 'src', 'main', 'assets', 'datasets', 'gbv_dataset.csv')
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), 'output')
os.makedirs(OUTPUT_DIR, exist_ok=True)

# --- Feature extraction matching the app's GBVModelTrainer.java ---

ABUSE_WORDS = ["controlled", "trapped", "watched", "freedom", "safe", "danger",
               "threat", "scared", "afraid", "unsafe", "threatened"]
DISTRESS_WORDS = ["stress", "weird", "overthinking", "difficult", "tense",
                  "worried", "uneasy", "anxious", "not myself"]
CONTROL_WORDS = ["have to ask", "can't decide", "don't have freedom",
                 "controlled", "need permission", "not allowed", "must get approval"]
URGENCY_WORDS = ["help", "emergency", "urgent", "immediate", "now",
                 "please help", "save me", "danger", "sos"]
ISOLATION_WORDS = ["can't talk to", "not allowed to see", "cut off", "isolated",
                   "alone", "no contact", "forbidden", "restricted"]
MANIPULATION_WORDS = ["it's my fault", "i deserved it", "i'm overthinking",
                      "maybe i'm wrong", "it's not that bad", "i'm being dramatic"]
VIOLENCE_WORDS = ["hit", "beat", "punch", "slap", "kick", "hurt", "pain",
                  "bruise", "bleed", "weapon", "knife", "gun"]
EMERGENCY_PHRASES = ["not safe", "in danger", "being followed", "someone is here",
                     "help me", "call police", "hear something", "break in",
                     "attacked", "assaulted"]

def extract_features(conversation):
    """Extract 100-dimensional feature vector from conversation text."""
    features = np.zeros(100, dtype=np.float32)
    lower = conversation.lower()

    # Word category counts (features 0-7)
    for w in ABUSE_WORDS:
        features[0] += lower.count(w)
    for w in DISTRESS_WORDS:
        features[1] += lower.count(w)
    for w in CONTROL_WORDS:
        features[2] += lower.count(w)
    for w in URGENCY_WORDS:
        features[3] += lower.count(w)
    for w in ISOLATION_WORDS:
        features[4] += lower.count(w)
    for w in MANIPULATION_WORDS:
        features[5] += lower.count(w)
    for w in VIOLENCE_WORDS:
        features[6] += lower.count(w)
    for w in EMERGENCY_PHRASES:
        features[7] += lower.count(w)

    # Text statistics (features 8-14)
    features[8] = len(conversation) / 1000.0  # normalized length
    words = lower.split()
    features[9] = len(words) / 200.0  # normalized word count
    features[10] = lower.count('?') / 10.0  # question marks
    features[11] = lower.count('!') / 10.0  # exclamation marks
    features[12] = lower.count('...') / 5.0  # ellipsis (hesitation)
    features[13] = lower.count('|') / 20.0  # conversation turns
    features[14] = lower.count('U:') / 10.0  # user utterances

    # Sentiment indicators (features 15-24)
    negative = ["not", "no", "don't", "can't", "won't", "never", "nothing", "nowhere"]
    positive = ["fine", "okay", "good", "normal", "talked", "resolved", "better"]
    fear_words = ["scared", "afraid", "terrified", "panic", "fear", "frightened"]
    anger_words = ["angry", "furious", "mad", "rage", "violent", "aggressive"]

    for w in negative:
        features[15] += lower.count(w)
    for w in positive:
        features[16] += lower.count(w)
    for w in fear_words:
        features[17] += lower.count(w)
    for w in anger_words:
        features[18] += lower.count(w)

    # Relationship context (features 19-23)
    features[19] = 1.0 if "partner" in lower or "relationship" in lower else 0.0
    features[20] = 1.0 if "family" in lower or "home" in lower or "house" in lower else 0.0
    features[21] = 1.0 if "public" in lower or "outside" in lower or "street" in lower else 0.0
    features[22] = 1.0 if "work" in lower or "office" in lower or "boss" in lower else 0.0
    features[23] = 1.0 if "school" in lower or "teacher" in lower else 0.0

    # Escalation indicators (features 24-29)
    features[24] = 1.0 if "please help" in lower or "help me" in lower else 0.0
    features[25] = 1.0 if "not safe" in lower or "unsafe" in lower else 0.0
    features[26] = 1.0 if "call police" in lower or "call 911" in lower else 0.0
    features[27] = 1.0 if "being followed" in lower else 0.0
    features[28] = 1.0 if "someone is" in lower or "hear something" in lower else 0.0
    features[29] = 1.0 if "break" in lower or "weapon" in lower else 0.0

    # Bigram features: common GBV phrase fragments (features 30-49)
    bigrams = [
        "i feel", "feel safe", "not safe", "let me", "can't go",
        "have to", "told me", "makes me", "won't let", "doesn't let",
        "not normal", "something wrong", "feels wrong", "doesn't feel",
        "need help", "get out", "run away", "locked in", "no one",
        "all alone"
    ]
    for i, bg in enumerate(bigrams):
        features[30 + i] = float(lower.count(bg))

    # Conversation dynamics (features 50-59)
    turns = conversation.split('|')
    user_turns = [t for t in turns if t.strip().startswith('U:')]
    assistant_turns = [t for t in turns if t.strip().startswith('A:')]
    features[50] = len(user_turns) / 10.0
    features[51] = len(assistant_turns) / 10.0
    features[52] = len(user_turns) / max(len(assistant_turns), 1)

    # Average user message length
    if user_turns:
        avg_len = np.mean([len(t.strip()) for t in user_turns])
        features[53] = avg_len / 100.0

    # Repetition detection (same phrases repeated = distress indicator)
    user_texts = [t.strip().replace('U:', '').strip().lower() for t in user_turns]
    unique_ratio = len(set(user_texts)) / max(len(user_texts), 1)
    features[54] = 1.0 - unique_ratio  # higher = more repetition

    # Emotional intensity (features 60-69)
    intensity_words = ["very", "really", "extremely", "so much", "terrible",
                       "horrible", "worst", "unbearable", "desperate", "critical"]
    for i, w in enumerate(intensity_words):
        features[60 + i] = float(w in lower)

    # Normalize first 8 features (category counts)
    for i in range(8):
        features[i] = min(1.0, features[i] / 10.0)

    # Normalize sentiment counts
    for i in range(15, 19):
        features[i] = min(1.0, features[i] / 10.0)

    return features


def load_dataset():
    """Load and parse the CSV dataset."""
    conversations = []
    labels = []
    risk_scores = []
    urgency_levels = []

    with open(DATASET_PATH, encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            conv = row.get('conversation', '').strip()
            label = row.get('label', '').strip().lower()
            risk = float(row.get('risk_score', 0))
            urgency = row.get('urgency_level', '').strip().lower()

            if conv and label in ('abuse', 'distress', 'emergency', 'normal'):
                conversations.append(conv)
                labels.append(label)
                risk_scores.append(risk)
                urgency_levels.append(urgency)

    return conversations, labels, risk_scores, urgency_levels


def build_model(input_dim=100, num_classes=4):
    """Build a neural network for GBV text classification."""
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(input_dim,)),
        tf.keras.layers.Dense(128, activation='relu'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(32, activation='relu'),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(num_classes, activation='softmax')
    ])
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    return model


def main():
    print("=" * 60)
    print("GBV TEXT MODEL TRAINING")
    print("=" * 60)

    # 1. Load data
    print("\n[1/5] Loading dataset...")
    conversations, labels, risk_scores, urgency_levels = load_dataset()
    print(f"  Loaded {len(conversations)} samples")

    # 2. Extract features
    print("\n[2/5] Extracting features...")
    X = np.array([extract_features(c) for c in conversations])
    print(f"  Feature matrix shape: {X.shape}")

    # Encode labels: abuse=0, distress=1, emergency=2, normal=3
    le = LabelEncoder()
    y = le.fit_transform(labels)
    print(f"  Classes: {list(le.classes_)}")
    for cls_name in le.classes_:
        count = sum(1 for l in labels if l == cls_name)
        print(f"    {cls_name}: {count}")

    # 3. Split data
    print("\n[3/5] Splitting data (80/20)...")
    X_train, X_val, y_train, y_val = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y)
    print(f"  Training: {len(X_train)}, Validation: {len(X_val)}")

    # 4. Train
    print("\n[4/5] Training model...")
    model = build_model(input_dim=100, num_classes=len(le.classes_))
    model.summary()

    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=100,
        batch_size=64,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(
                patience=15, restore_best_weights=True, monitor='val_accuracy'),
            tf.keras.callbacks.ReduceLROnPlateau(
                patience=5, factor=0.5, monitor='val_loss')
        ],
        verbose=1
    )

    # Evaluate
    val_loss, val_acc = model.evaluate(X_val, y_val, verbose=0)
    print(f"\n  Final Validation Accuracy: {val_acc:.4f}")
    print(f"  Final Validation Loss: {val_loss:.4f}")

    # Per-class accuracy
    y_pred = np.argmax(model.predict(X_val, verbose=0), axis=1)
    for i, cls_name in enumerate(le.classes_):
        mask = y_val == i
        if mask.sum() > 0:
            cls_acc = (y_pred[mask] == i).mean()
            print(f"    {cls_name}: {cls_acc:.4f} ({mask.sum()} samples)")

    # 5. Convert to TFLite
    print("\n[5/5] Converting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    output_path = os.path.join(OUTPUT_DIR, 'gbv_text_model.tflite')
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    print(f"  Saved: {output_path} ({len(tflite_model)} bytes)")

    # Verify TFLite model
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    print(f"  TFLite Input shape: {input_details[0]['shape']}")
    print(f"  TFLite Output shape: {output_details[0]['shape']}")

    # Test inference
    test_sample = X_val[0:1].astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], test_sample)
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]['index'])
    pred_class = le.classes_[np.argmax(output[0])]
    actual_class = le.classes_[y_val[0]]
    print(f"  Test prediction: {pred_class} (actual: {actual_class})")
    print(f"  Output probabilities: {output[0]}")

    # Save label mapping
    label_map_path = os.path.join(OUTPUT_DIR, 'gbv_text_labels.txt')
    with open(label_map_path, 'w') as f:
        for i, cls_name in enumerate(le.classes_):
            f.write(f"{i}:{cls_name}\n")
    print(f"  Label mapping saved: {label_map_path}")

    print("\n" + "=" * 60)
    print("TEXT MODEL TRAINING COMPLETE")
    print("=" * 60)


if __name__ == '__main__':
    main()
