# Backup Keys for Google Drive and Dropbox

Google Drive and Dropbox only let an app sign people in with that app's own keys. ShackScreen's release builds include them; a build from source needs its own. S3-compatible and WebDAV backups need no keys.

The keys are not secret in the way a password is: anyone can pull them out of the APK, and that's how these "installed app" sign-ins are designed. They're still kept out of the repo, so a fork doesn't use ShackScreen's.

## Where they go

Create `oauth.properties` next to the signing key (`../Signing/oauth.properties` from the repo), or point `SHACKSCREEN_OAUTH` at it:

```properties
googleClientId=1234567890-abc.apps.googleusercontent.com
googleClientSecret=GOCSPX-...
dropboxAppKey=abcd1234efgh567
```

Then build as usual. Settings → Backup shows "not in this build" next to a service whose key is missing.

## Google Drive

1. Go to [console.cloud.google.com](https://console.cloud.google.com) and create a project (free).
2. **APIs & Services → Library**: enable the **Google Drive API**.
3. **APIs & Services → OAuth consent screen**: choose **External**, fill in the app name (ShackScreen), your email and the support email. Add the scope `.../auth/drive.file` (ShackScreen can only see files it made). Then **Publish app**: this scope doesn't need Google's review.
4. **APIs & Services → Credentials → Create credentials → OAuth client ID**, application type **TVs and Limited Input devices**.
5. Copy the **Client ID** and **Client secret** into `googleClientId` and `googleClientSecret`.

## Dropbox

1. Go to [dropbox.com/developers/apps](https://www.dropbox.com/developers/apps) and **Create app**.
2. Choose **Scoped access** and **App folder**, and name it ShackScreen (the folder becomes Apps/ShackScreen).
3. On the **Permissions** tab, tick `files.content.write` and `files.content.read`, and **Submit**.
4. On the **Settings** tab, copy the **App key** into `dropboxAppKey`. No redirect address is needed: ShackScreen shows the code on your phone and you type it on the TV.
5. A new app works for up to 50 people. Before sharing the build widely, use **Apply for production** on the Settings tab (free).
