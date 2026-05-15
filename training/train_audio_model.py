"""
Train an Audio Emergency Detection TFLite model.

Since there are no real audio recordings in the project, this script:
1. Generates realistic synthetic audio training data using signal processing
   - Emergency sounds: distress frequencies, rapid modulation, high amplitude
   - Normal sounds: steady tones, low amplitude, ambient patterns
2. Extracts MFCC features matching the app's expected input [1, 40, 431, 1]
3. Trains a CNN model for binary classification (emergency vs normal)
4. Converts to TFLite and replaces sos_audio_model.tflite

The synthetic data simulates:
- Emergency: screaming (fundamental 300-3000 Hz with harmonics + amplitude modulation),
  crying (200-600 Hz with tremolo), shouting (sharp onsets, high energy bursts),
  glass breaking (broadband impulse noise)
- Normal: speech (100-300 Hz fundamentals), ambient noise (pink/white noise),
  music (harmonic tones), silence, traffic (low-frequency rumble)
"""

import os
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), 'output')
os.makedirs(OUTPUT_DIR, exist_ok=True)

SAMPLE_RATE = 16000
N_MFCC = 40
N_TIME_STEPS = 431
DURATION_SEC = N_TIME_STEPS * 512 / SAMPLE_RATE  # ~13.8 seconds at hop=512
N_SAMPLES_PER_CLASS = 1500  # Generate 1500 emergency + 1500 normal = 3000 total


def generate_sine_with_harmonics(freq, duration, sr, n_harmonics=5, amplitude=0.5):
    """Generate a tone with harmonics (simulates voice fundamentals)."""
    t = np.linspace(0, duration, int(sr * duration), endpoint=False)
    signal = np.zeros_like(t)
    for h in range(1, n_harmonics + 1):
        harmonic_amp = amplitude / (h ** 1.2)
        signal += harmonic_amp * np.sin(2 * np.pi * freq * h * t)
    return signal


def apply_amplitude_modulation(signal, mod_freq, mod_depth=0.5):
    """Apply amplitude modulation (tremolo effect for crying/distress)."""
    t = np.linspace(0, len(signal) / SAMPLE_RATE, len(signal), endpoint=False)
    modulator = 1.0 + mod_depth * np.sin(2 * np.pi * mod_freq * t)
    return signal * modulator


def apply_frequency_modulation(freq, duration, sr, mod_freq, mod_depth):
    """Generate a frequency-modulated signal (pitch variation in screams)."""
    t = np.linspace(0, duration, int(sr * duration), endpoint=False)
    phase = 2 * np.pi * freq * t + mod_depth * np.sin(2 * np.pi * mod_freq * t)
    return np.sin(phase)


def generate_emergency_audio(rng):
    """Generate synthetic emergency audio."""
    duration = DURATION_SEC + 0.5
    n_samples = int(SAMPLE_RATE * duration)
    signal = np.zeros(n_samples)

    emergency_type = rng.choice(['scream', 'cry', 'shout', 'struggle', 'mixed'])

    if emergency_type == 'scream':
        freq = rng.uniform(500, 2500)
        scream = apply_frequency_modulation(freq, duration, SAMPLE_RATE,
                                           mod_freq=rng.uniform(3, 12),
                                           mod_depth=rng.uniform(200, 800))
        scream *= rng.uniform(0.6, 1.0)
        scream = apply_amplitude_modulation(scream, rng.uniform(5, 15), 0.4)
        # Add harmonics for realism
        for h in range(2, rng.integers(3, 7)):
            harmonic = apply_frequency_modulation(freq * h, duration, SAMPLE_RATE,
                                                  mod_freq=rng.uniform(2, 8),
                                                  mod_depth=rng.uniform(100, 400))
            harmonic *= rng.uniform(0.1, 0.3) / h
            scream += harmonic
        signal += scream

    elif emergency_type == 'cry':
        freq = rng.uniform(250, 600)
        cry = generate_sine_with_harmonics(freq, duration, SAMPLE_RATE,
                                          n_harmonics=rng.integers(3, 8),
                                          amplitude=rng.uniform(0.4, 0.8))
        cry = apply_amplitude_modulation(cry, rng.uniform(4, 10), rng.uniform(0.3, 0.7))
        # Add sobs (periodic amplitude drops)
        sob_freq = rng.uniform(1.5, 4.0)
        t = np.linspace(0, duration, n_samples, endpoint=False)
        sob_envelope = 0.5 + 0.5 * np.abs(np.sin(2 * np.pi * sob_freq * t))
        cry *= sob_envelope
        signal += cry

    elif emergency_type == 'shout':
        # Sharp onset bursts
        n_bursts = rng.integers(3, 10)
        for _ in range(n_bursts):
            burst_start = rng.integers(0, n_samples - SAMPLE_RATE)
            burst_len = rng.integers(SAMPLE_RATE // 4, SAMPLE_RATE)
            freq = rng.uniform(200, 1500)
            burst = generate_sine_with_harmonics(freq, burst_len / SAMPLE_RATE,
                                                SAMPLE_RATE, n_harmonics=5,
                                                amplitude=rng.uniform(0.5, 1.0))
            # Ensure exact length match
            burst = burst[:burst_len]
            if len(burst) < burst_len:
                burst = np.pad(burst, (0, burst_len - len(burst)))
            # Sharp attack envelope
            envelope = np.ones(len(burst))
            attack = min(int(SAMPLE_RATE * 0.02), len(burst))
            envelope[:attack] = np.linspace(0, 1, attack)
            decay = min(int(SAMPLE_RATE * 0.1), len(burst))
            envelope[-decay:] = np.linspace(1, 0, decay)
            burst *= envelope
            end = min(burst_start + burst_len, n_samples)
            signal[burst_start:end] += burst[:end - burst_start]

    elif emergency_type == 'struggle':
        # Chaotic noise bursts with tonal elements
        noise = rng.normal(0, rng.uniform(0.2, 0.5), n_samples)
        # Band-pass filter effect (emphasize 200-3000 Hz)
        from scipy.signal import butter, filtfilt
        b, a = butter(4, [200 / (SAMPLE_RATE / 2), 3000 / (SAMPLE_RATE / 2)], btype='band')
        noise = filtfilt(b, a, noise)
        # Add intermittent voice
        voice_freq = rng.uniform(200, 800)
        voice = generate_sine_with_harmonics(voice_freq, duration, SAMPLE_RATE,
                                            n_harmonics=4, amplitude=0.3)
        t = np.linspace(0, duration, n_samples, endpoint=False)
        voice_gate = (np.sin(2 * np.pi * rng.uniform(1, 3) * t) > 0).astype(float)
        signal += noise + voice * voice_gate

    else:  # mixed
        # Combine elements
        freq1 = rng.uniform(400, 2000)
        scream_part = apply_frequency_modulation(freq1, duration, SAMPLE_RATE,
                                                mod_freq=rng.uniform(5, 10),
                                                mod_depth=rng.uniform(300, 600))
        scream_part *= rng.uniform(0.4, 0.8)

        freq2 = rng.uniform(250, 500)
        cry_part = generate_sine_with_harmonics(freq2, duration, SAMPLE_RATE,
                                               n_harmonics=4, amplitude=0.3)
        cry_part = apply_amplitude_modulation(cry_part, rng.uniform(3, 8), 0.5)

        noise = rng.normal(0, 0.15, n_samples)
        signal += scream_part + cry_part + noise[:n_samples]

    # Add background noise
    bg_noise = rng.normal(0, rng.uniform(0.02, 0.1), n_samples)
    signal += bg_noise

    # Normalize
    peak = np.max(np.abs(signal))
    if peak > 0:
        signal = signal / peak * rng.uniform(0.6, 0.95)

    return signal.astype(np.float32)


def generate_normal_audio(rng):
    """Generate synthetic normal/non-emergency audio."""
    duration = DURATION_SEC + 0.5
    n_samples = int(SAMPLE_RATE * duration)
    signal = np.zeros(n_samples)

    normal_type = rng.choice(['speech', 'ambient', 'music', 'silence', 'traffic'])

    if normal_type == 'speech':
        # Calm speech: low fundamental + gentle modulation
        freq = rng.uniform(80, 250)
        speech = generate_sine_with_harmonics(freq, duration, SAMPLE_RATE,
                                             n_harmonics=rng.integers(3, 6),
                                             amplitude=rng.uniform(0.1, 0.3))
        # Natural speech rhythm (syllable-like modulation)
        t = np.linspace(0, duration, n_samples, endpoint=False)
        syllable_mod = 0.5 + 0.5 * np.abs(np.sin(2 * np.pi * rng.uniform(2, 5) * t))
        speech *= syllable_mod
        # Pauses
        n_pauses = rng.integers(3, 8)
        for _ in range(n_pauses):
            pause_start = rng.integers(0, n_samples - SAMPLE_RATE // 2)
            pause_len = rng.integers(SAMPLE_RATE // 4, SAMPLE_RATE)
            end = min(pause_start + pause_len, n_samples)
            fade = np.linspace(1, 0, end - pause_start)
            speech[pause_start:end] *= fade
        signal += speech

    elif normal_type == 'ambient':
        # Pink noise (ambient environment)
        white = rng.normal(0, 1, n_samples)
        # Simple pink noise approximation
        from scipy.signal import butter, filtfilt
        b, a = butter(2, 2000 / (SAMPLE_RATE / 2), btype='low')
        pink = filtfilt(b, a, white)
        pink *= rng.uniform(0.05, 0.15)
        signal += pink

    elif normal_type == 'music':
        # Simple musical tones
        base_freq = rng.choice([261.6, 293.7, 329.6, 349.2, 392.0, 440.0])
        t = np.linspace(0, duration, n_samples, endpoint=False)
        music = np.sin(2 * np.pi * base_freq * t) * 0.2
        # Add chord
        music += np.sin(2 * np.pi * base_freq * 1.25 * t) * 0.1
        music += np.sin(2 * np.pi * base_freq * 1.5 * t) * 0.08
        # Gentle amplitude variation
        music *= 0.5 + 0.3 * np.sin(2 * np.pi * 0.5 * t)
        signal += music

    elif normal_type == 'silence':
        # Near-silence with minimal ambient noise
        signal = rng.normal(0, rng.uniform(0.001, 0.02), n_samples)

    else:  # traffic
        # Low-frequency rumble
        from scipy.signal import butter, filtfilt
        white = rng.normal(0, 1, n_samples)
        b, a = butter(3, 500 / (SAMPLE_RATE / 2), btype='low')
        traffic = filtfilt(b, a, white)
        traffic *= rng.uniform(0.1, 0.25)
        # Occasional car pass (amplitude swell)
        n_cars = rng.integers(2, 6)
        for _ in range(n_cars):
            car_center = rng.integers(SAMPLE_RATE, n_samples - SAMPLE_RATE)
            car_width = rng.integers(SAMPLE_RATE, SAMPLE_RATE * 3)
            car_env = np.exp(-0.5 * ((np.arange(n_samples) - car_center) / (car_width / 4)) ** 2)
            traffic += car_env * rng.uniform(0.1, 0.3)
        signal += traffic

    # Add very light background noise
    bg_noise = rng.normal(0, rng.uniform(0.005, 0.03), n_samples)
    signal += bg_noise

    # Normalize to lower amplitude (normal sounds are quieter)
    peak = np.max(np.abs(signal))
    if peak > 0:
        signal = signal / peak * rng.uniform(0.1, 0.5)

    return signal.astype(np.float32)


def extract_mfcc(audio, sr=SAMPLE_RATE, n_mfcc=N_MFCC, n_time_steps=N_TIME_STEPS):
    """Extract MFCC features matching app's expected shape [40, 431, 1]."""
    import librosa
    mfcc = librosa.feature.mfcc(y=audio, sr=sr, n_mfcc=n_mfcc, hop_length=512, n_fft=1024)

    # Pad or truncate to exactly n_time_steps
    if mfcc.shape[1] < n_time_steps:
        pad_width = n_time_steps - mfcc.shape[1]
        mfcc = np.pad(mfcc, ((0, 0), (0, pad_width)), mode='constant')
    else:
        mfcc = mfcc[:, :n_time_steps]

    # Normalize per-feature
    mean = mfcc.mean(axis=1, keepdims=True)
    std = mfcc.std(axis=1, keepdims=True) + 1e-8
    mfcc = (mfcc - mean) / std

    return mfcc.reshape(n_mfcc, n_time_steps, 1)


def build_audio_model():
    """Build CNN matching the app's expected input shape [1, 40, 431, 1]."""
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(N_MFCC, N_TIME_STEPS, 1)),

        tf.keras.layers.Conv2D(32, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.MaxPooling2D((2, 2)),
        tf.keras.layers.Dropout(0.25),

        tf.keras.layers.Conv2D(64, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.MaxPooling2D((2, 2)),
        tf.keras.layers.Dropout(0.25),

        tf.keras.layers.Conv2D(128, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.MaxPooling2D((2, 2)),
        tf.keras.layers.Dropout(0.3),

        tf.keras.layers.Conv2D(256, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.GlobalAveragePooling2D(),

        tf.keras.layers.Dense(128, activation='relu'),
        tf.keras.layers.Dropout(0.4),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(1, activation='sigmoid')  # 0=normal, 1=emergency
    ])

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.0005),
        loss='binary_crossentropy',
        metrics=['accuracy']
    )
    return model


def main():
    import librosa  # Import here to catch any issues early

    print("=" * 60)
    print("AUDIO EMERGENCY DETECTION MODEL TRAINING")
    print("=" * 60)

    rng = np.random.default_rng(42)

    # 1. Generate synthetic audio data
    print(f"\n[1/5] Generating {N_SAMPLES_PER_CLASS * 2} synthetic audio samples...")
    X_list = []
    y_list = []

    for i in range(N_SAMPLES_PER_CLASS):
        if i % 100 == 0:
            print(f"  Emergency: {i}/{N_SAMPLES_PER_CLASS}")
        audio = generate_emergency_audio(rng)
        mfcc = extract_mfcc(audio)
        X_list.append(mfcc)
        y_list.append(1.0)

    for i in range(N_SAMPLES_PER_CLASS):
        if i % 100 == 0:
            print(f"  Normal: {i}/{N_SAMPLES_PER_CLASS}")
        audio = generate_normal_audio(rng)
        mfcc = extract_mfcc(audio)
        X_list.append(mfcc)
        y_list.append(0.0)

    X = np.array(X_list, dtype=np.float32)
    y = np.array(y_list, dtype=np.float32)
    print(f"  Dataset shape: X={X.shape}, y={y.shape}")

    # 2. Split data
    print("\n[2/5] Splitting data (80/20)...")
    X_train, X_val, y_train, y_val = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y)
    print(f"  Training: {len(X_train)}, Validation: {len(X_val)}")

    # 3. Build model
    print("\n[3/5] Building CNN model...")
    model = build_audio_model()
    model.summary()

    # 4. Train
    print("\n[4/5] Training model...")
    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=50,
        batch_size=32,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(
                patience=10, restore_best_weights=True, monitor='val_accuracy'),
            tf.keras.callbacks.ReduceLROnPlateau(
                patience=5, factor=0.5, monitor='val_loss')
        ],
        verbose=1
    )

    val_loss, val_acc = model.evaluate(X_val, y_val, verbose=0)
    print(f"\n  Final Validation Accuracy: {val_acc:.4f}")
    print(f"  Final Validation Loss: {val_loss:.4f}")

    # Detailed evaluation
    y_pred = (model.predict(X_val, verbose=0) > 0.5).astype(int).flatten()
    y_val_int = y_val.astype(int)
    tp = np.sum((y_pred == 1) & (y_val_int == 1))
    tn = np.sum((y_pred == 0) & (y_val_int == 0))
    fp = np.sum((y_pred == 1) & (y_val_int == 0))
    fn = np.sum((y_pred == 0) & (y_val_int == 1))
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0
    print(f"  Emergency Precision: {precision:.4f}")
    print(f"  Emergency Recall: {recall:.4f}")
    print(f"  Emergency F1: {f1:.4f}")
    print(f"  TP={tp}, TN={tn}, FP={fp}, FN={fn}")

    # 5. Convert to TFLite
    print("\n[5/5] Converting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    output_path = os.path.join(OUTPUT_DIR, 'sos_audio_model.tflite')
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    print(f"  Saved: {output_path} ({len(tflite_model)} bytes, {len(tflite_model)/(1024*1024):.2f} MB)")

    # Verify TFLite model
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    print(f"  TFLite Input shape: {input_details[0]['shape']}")
    print(f"  TFLite Output shape: {output_details[0]['shape']}")

    # Test with validation samples
    correct = 0
    total = min(100, len(X_val))
    for i in range(total):
        sample = X_val[i:i+1].astype(np.float32)
        interpreter.set_tensor(input_details[0]['index'], sample)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]['index'])
        pred = 1 if output[0][0] > 0.5 else 0
        if pred == int(y_val[i]):
            correct += 1
    print(f"  TFLite Accuracy (first {total} val samples): {correct/total:.4f}")

    print("\n" + "=" * 60)
    print("AUDIO MODEL TRAINING COMPLETE")
    print("=" * 60)
    print(f"\nModel saved to: {output_path}")
    print(f"Copy to: app/src/main/assets/sos_audio_model.tflite")


if __name__ == '__main__':
    main()
