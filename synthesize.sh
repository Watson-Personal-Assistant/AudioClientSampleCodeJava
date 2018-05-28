#!/bin/sh
if [ -z "${TTS_USERNAME:-}" ]; then
 echo 'TTS_USERNAME is not defined.'
 exit 1
fi

if [ -z "${TTS_PASSWORD:-}" ]; then
 echo 'TTS_PASSWORD is not defined.'
 exit 1
fi

mkdir -p speech

TTS_VOICE=en-US_LisaVoice

error_exit()
{
  rm speech/$1
  echo >&2 "Error generating audio file $1, error code $2"
  exit $2
}

synthesize()
{
  TEXT=$(echo $1 | sed 's/ /%20/g')
  NAME=$2
  curl -f -X GET -u "$TTS_USERNAME":"$TTS_PASSWORD" \
    --silent \
    "https://stream.watsonplatform.net/text-to-speech/api/v1/synthesize?accept=audio/flac&text=$TEXT&voice=$TTS_VOICE" \
  > speech/$NAME.flac || error_exit $NAME.flac $?
}

synthesize "The configure properties file was not found" error-no-config-file
synthesize "There was an error with the configuration of the client. Check the configure.properties file for valid values." error-config
synthesize "There was an error connecting to the server" error-network
synthesize "There was an error authenticating with the server" error-auth
synthesize "Your IP address is" announce-ip
synthesize "The client has stopped" aborting
synthesize dot dot

for digit in `seq 0 9`
do
  synthesize $digit $digit
done
