function AudioWaveform() {
  return <div className="audio-waveform" aria-label="Audio waveform" role="img">{Array.from({ length: 32 }, (_, index) => <i key={index} style={{ height: `${20 + ((index * 17) % 55)}%` }} />)}</div>
}

export default AudioWaveform
