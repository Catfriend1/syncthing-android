On "old" phones with low hardware specs, SyncthingNative might crash or exhaust resources while the user is only syncing a small set of data.

Workaround:
- Go to Settings > Troubleshooting > Environment variables
- Enter the following content:
> GOMAXPROCS=2
- Exit and restart the app
