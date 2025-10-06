Feedback from user @raxod502, edited by app maintainer.

* Description of the issue

The "Import and Export" screen has insufficient details on what "relative path" means

* Steps to reproduce

Go to Settings > Import and Export. Under the Prepare section, there's an option labeled "Relative path to config archive". However, it's hard to tell what should be entered here, since the phrase "relative path" implies that it is relative to something, and it is not specified what it is relative to. On desktop systems, it would be assumed that it is the current working directory, but Android does not have such a concept, nor a "default directory" for an app. If clicking on the option revealed a file picker then it would disambiguate, but since it is a free-form text entry field, there is not really any way to be confident about what should be entered, without trying something and seeing what happens.

It's relative to the general document storage folder, i.e. /sdcard, /storage/emulated/0. Also, if you provide an absolute path, the leading slash is stripped off and it's treated as a relative path.
