#!/bin/sh
#
# Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
# and other contributors as indicated by the @author tags.
#
# Licensed under the Eclipse Public License - v 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# https://www.eclipse.org/legal/epl-2.0/
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


DIRNAME=`dirname "$0"`
PROGNAME=`basename "$0"`
GREP="grep"

# Use the maximum available, or set MAX_FD != -1 to use that
MAX_FD="maximum"

# tell linux glibc how many memory pools can be created that are used by malloc
MALLOC_ARENA_MAX="${MALLOC_ARENA_MAX:-1}"
export MALLOC_ARENA_MAX

# OS specific support (must be 'true' or 'false').
cygwin=false;
darwin=false;
linux=false;
solaris=false;
freebsd=false;
other=false
case "`uname`" in
    CYGWIN*)
        cygwin=true
        ;;

    Darwin*)
        darwin=true
        ;;
    FreeBSD)
        freebsd=true
        ;;
    Linux)
        linux=true
        ;;
    SunOS*)
        solaris=true
        ;;
    *)
        other=true
        ;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
    [ -n "$SEARCHPE_HOME" ] &&
        SEARCHPE_HOME=`cygpath --unix "$SEARCHPE_HOME"`
    [ -n "$JAVA_HOME" ] &&
        JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
    [ -n "$JAVAC_JAR" ] &&
        JAVAC_JAR=`cygpath --unix "$JAVAC_JAR"`
fi

# Setup SEARCHPE_HOME
RESOLVED_SEARCHPE_HOME=`cd "$DIRNAME/.." >/dev/null; pwd`
if [ "x$SEARCHPE_HOME" = "x" ]; then
    # get the full path (without any relative bits)
    SEARCHPE_HOME=$RESOLVED_SEARCHPE_HOME
else
 SANITIZED_SEARCHPE_HOME=`cd "$SEARCHPE_HOME"; pwd`
 if [ "$RESOLVED_SEARCHPE_HOME" != "$SANITIZED_SEARCHPE_HOME" ]; then
   echo ""
   echo "   WARNING:  SEARCHPE_HOME may be pointing to a different installation - unpredictable results may occur."
   echo ""
   echo "             SEARCHPE_HOME: $SEARCHPE_HOME"
   echo ""
   sleep 2s
 fi
fi
export SEARCHPE_HOME

# Setup the JVM
if [ "x$JAVA" = "x" ]; then
    if [ "x$JAVA_HOME" != "x" ]; then
        JAVA="$JAVA_HOME/bin/java"
    else
        JAVA="java"
    fi
fi

# determine the default base dir, if not set
if [ "x$SEARCHPE_BASE_DIR" = "x" ]; then
   SEARCHPE_BASE_DIR="$SEARCHPE_HOME/standalone"
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
    SEARCHPE_HOME=`cygpath --path --windows "$SEARCHPE_HOME"`
    JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
fi

# Display our environment
echo "========================================================================="
echo ""
echo "  Searchpe Bootstrap Environment"
echo ""
echo "  SEARCHPE_HOME: $SEARCHPE_HOME"
echo ""
echo "  JAVA: $JAVA"
echo ""
echo "  JAVA_OPTS: $JAVA_OPTS"
echo ""
echo "========================================================================="
echo ""

cd "$SEARCHPE_HOME";
while true; do
   if [ "x$LAUNCH_SEARCHPE_IN_BACKGROUND" = "x" ]; then
      # Execute the JVM in the foreground
      eval \"$JAVA\" $JAVA_OPTS \
         -jar \""$SEARCHPE_HOME"/searchpe.jar\"
      SEARCHPE_STATUS=$?
   else
      # Execute the JVM in the background
      eval \"$JAVA\" $JAVA_OPTS \
         -jar \""$SEARCHPE_HOME"/searchpe.jar\"
      SEARCHPE_PID=$!
      # Trap common signals and relay them to the searchpe process
      trap "kill -HUP  $SEARCHPE_PID" HUP
      trap "kill -TERM $SEARCHPE_PID" INT
      trap "kill -QUIT $SEARCHPE_PID" QUIT
      trap "kill -PIPE $SEARCHPE_PID" PIPE
      trap "kill -TERM $SEARCHPE_PID" TERM
      if [ "x$SEARCHPE_PIDFILE" != "x" ]; then
        echo $SEARCHPE_PID > $SEARCHPE_PIDFILE
      fi
      # Wait until the background process exits
      WAIT_STATUS=128
      while [ "$WAIT_STATUS" -ge 128 ]; do
         wait $SEARCHPE_PID 2>/dev/null
         WAIT_STATUS=$?
         if [ "$WAIT_STATUS" -gt 128 ]; then
            SIGNAL=`expr $WAIT_STATUS - 128`
            SIGNAL_NAME=`kill -l $SIGNAL`
            echo "*** SearchpeAS process ($SEARCHPE_PID) received $SIGNAL_NAME signal ***" >&2
         fi
      done
      if [ "$WAIT_STATUS" -lt 127 ]; then
         SEARCHPE_STATUS=$WAIT_STATUS
      else
         SEARCHPE_STATUS=0
      fi
      if [ "$SEARCHPE_STATUS" -ne 10 ]; then
            # Wait for a complete shudown
            wait $SEARCHPE_PID 2>/dev/null
      fi
      if [ "x$SEARCHPE_PIDFILE" != "x" ]; then
            grep "$SEARCHPE_PID" $SEARCHPE_PIDFILE && rm $SEARCHPE_PIDFILE
      fi
   fi
   if [ "$SEARCHPE_STATUS" -eq 10 ]; then
      echo "Restarting application server..."
   else
      exit $SEARCHPE_STATUS
   fi
done
