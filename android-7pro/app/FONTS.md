# Arabic fonts (optional, free)

7PRO's design uses two typefaces. The app builds and runs without them (it falls back to the
phone's default Arabic font); add the files to turn them on — no code change needed.

1. Download from Google Fonts (SIL Open Font License, free for commercial use):
   - Readex Pro: https://fonts.google.com/specimen/Readex+Pro
   - IBM Plex Sans Arabic: https://fonts.google.com/specimen/IBM+Plex+Sans+Arabic
2. Create the folder `app/src/main/res/font/` and copy in these files, renamed exactly
   (lowercase letters, digits and underscores only — an Android resource rule):

| Download                              | Rename to                    |
|---------------------------------------|------------------------------|
| ReadexPro-Regular.ttf                 | readexpro_regular.ttf        |
| ReadexPro-Medium.ttf                  | readexpro_medium.ttf         |
| ReadexPro-Bold.ttf                    | readexpro_bold.ttf           |
| IBMPlexSansArabic-Regular.ttf         | ibmplexarabic_regular.ttf    |
| IBMPlexSansArabic-Medium.ttf          | ibmplexarabic_medium.ttf     |
| IBMPlexSansArabic-SemiBold.ttf        | ibmplexarabic_semibold.ttf   |

If the Google Fonts download is a variable font (one file with several weights), use the
"Static" folder inside the zip instead. Any subset of the files works.

Headings (titles, greeting) use Readex Pro; body text and labels use IBM Plex Sans Arabic.
Latin text (English UI) keeps the system font.
