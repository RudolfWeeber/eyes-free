#!/usr/bin/python2.4
# Copyright 2008 Google Inc. All Rights Reserved.

""" Script to generate wav files for a bunch of letters, numbers,
 and short phrases using the Mac's built-in "say" command and the open-source"sndfile-convert" program (part of "libsndfile")
 On a big-endian machine (PPC Mac), replace '-s2 01' with '-s2 10', below
"""
 
 
import sys, os

def exists(filename):
  return os.access(filename, os.F_OK)

def run(cmd):
  print "    %s" % cmd
  status = os.system(cmd)
  if status != 0:
    sys.exit(status)

lexicon = [
  "button",
  "clear",
  "cleared",
  "dial",
  "dialing mode",
  "disabled",
  "edit text",
  "list item",
  "phone number",
  "text view",
  "you are about to dial"]

# alphabet
lexicon += [chr(i) for i in range(ord('a'), ord('z') + 1)]
# digits
lexicon += [chr(i) for i in range(ord('0'), ord('9') + 1)]

def main ():
  "Generate the samples we need."

  # create a 200-ms silent file to append
  num_silent_samples = 4410
  fp = open('silent_padding.raw', 'w')
  fp.write('\0' * num_silent_samples * 2)
  fp.close()

  for word in lexicon:
    filename = (word + '.wav').replace(' ', '_')
    if len(word) == 1 and ord(word) >= ord('0') and ord(word) <= ord('9'):
      filename = 'num' + filename
    if not exists(filename):
      print '%s -> %s' % (word, filename)
      # Execute TTS and get an AIFF file
      run('echo "[[rate 280]] %s" | say -f - -o tmp.aiff' % word)
      # Convert to RAW
      run('sndfile-convert tmp.aiff tmp.raw')
      # Get its length
      num_samples = len(open('tmp.raw').read()) / 2
      # Create a NIST header (easy text format for audio header)
      fp = open('header.nist', 'w')
      header_text = (
          'NIST_1A\n   1024\nchannel_count -i 1\nsample_rate -i 22050\n'
          'sample_n_bytes -i 2\nsample_sig_bits -i 16\n'
          'sample_coding -s3 pcm\nsample_byte_format -s2 01\n'
          'sample_count -i %d\n'
          'end_head') % (num_samples + num_silent_samples)
      # Write the header, padded to exactly 1024 bytes
      fp.write(header_text + ('\0' * (1024 - len(header_text))))
      fp.close()
      # Append header and silence
      run('cat header.nist tmp.raw silent_padding.raw > combined.nist')
      # Convert final result to WAV
      run('sndfile-convert combined.nist %s' % filename)
    else:
      print '%s -> %s (already exists)' % (word, filename)

  print "Cleaning up"
  run('rm -f header.nist')
  run('rm -f tmp.aiff')
  run('rm -f tmp.raw')
  run('rm -f silent_padding.raw')
  run('rm -f combined.nist')


if __name__ == '__main__':
  main()

