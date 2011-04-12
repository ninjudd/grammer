Get your Yammer notifications though Growl.

Grammer checks for Yammer notifications for you and turns them into Growl notifications.

## Installation

Grammer is written in Clojure and uses the [cake build tool](http://github.com/ninjudd/cake).
To build a standalone executable:

    git clone git://github.com/ninjudd/grammer.git
    cd grammer
    cake bin

Then you can start grammer with:

    ./grammer

Or just download the latest executable [here](http://cloud.github.com/downloads/ninjudd/grammer/grammer).

## Auto-start

1. Copy both the `grammer` executable and `install/grammer-launch` into `/usr/local/bin`

2. Copy `install/com.ninjudd.grammer.plist` into `~/Library/LaunchAgents`

3. `launchctl load ~/Library/LaunchAgents/com.ninjudd.grammer.plist`

## Debugging

This is alpha software at this point, but it works for me. If you have any problems, open
`Console.app` and search for `grammer`, then open an issue with the relevant information.