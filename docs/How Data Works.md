# How Data Works

Back to [[Home]]. Current as of version 0.5.1.

| Data | Source | How it reaches the app |
|---|---|---|
| 20-year climate | NASA POWER | Phone downloads 2006 to 2025 once per regional grid cell and saves it |
| Scores and risks | AgroShift Java engine | Calculated on the phone |
| Crop traits and calendars | Built-in table from the earlier project | Ships inside the app. Some values are approximate |
| Weather | Open-Meteo | Saved result appears first, then refreshes |
| Map and search | OpenStreetMap and Nominatim | Loaded when used |
| Satellite map | Esri World Imagery | Loaded when used |
| Fields and farm records | Farmer | Saved only on the phone for now |
| Mandi prices | AMIS Punjab | Official website opens outside the app |

## Assistant

1. The farmer selects a field and asks by text or voice.
2. The app sends the question, recent chat, language and that field's analysis to the existing FieldShift backend.
3. The analysis includes 20-year monthly NASA climate, rotation scores, risks, What-If results, crops, water access, priorities and soil choice.
4. The backend sends it to Gemini using a server-side key. Its rules require farm facts to come only from this analysis and block fertilizer or pesticide doses.
5. The answer returns to the app, has its own read-aloud and copy buttons, and is saved per field on the phone.

The exact coordinates and field name are not sent to the assistant. Chat languages: English, Urdu, Punjabi and Sindhi use the backend language setting. Saraiki is beta and uses a prompt instruction. Pashto, Balochi, Hindko and Brahui are shown as coming soon because the backend returned the wrong language for Pashto and Balochi. Read aloud uses voices installed on the phone. General farming advice can still come from the AI's agronomy knowledge, so it is not limited to official documents yet.

## Water and danger alerts

- Open-Meteo supplies 31 past days and 7 forecast days of rain, rain chance, reference evapotranspiration, temperature and wind.
- The irrigation answer is a simple soil-water estimate using the field's soil and saved last watering date. It does not yet use a crop coefficient, so it tells the farmer to check the soil by hand.
- If the last watering date or weather days are missing, the app asks for them instead of guessing.
- Danger alerts use AgroShift thresholds for the next three days. They are not official PMD or NDMA warnings.
- Canal turns are saved on the phone. Exact alarm timing needs the Android exact-alarm permission on newer phones.
