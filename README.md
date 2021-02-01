# Calendar with Benefits
This Project was created in context of the course on mobile applications
at the Baden-Wuerttemberg Cooperative State University in Mannheim.

## Android
The lowest supported API level is 16 (Android 4.1). The target API level is 29 (Android 10).
The application was tested on device with Android 10.

## APIs
This app accesses two external APIs. It needs to authorize itself by providing
two distinct keys, that can be requested free of charge.

1. RMV-Auskunft-API: [https://opendata.rmv.de/site/start.html]
2. OpenWeatherMap: [https://openweathermap.org/]

The keys need to be provided at build time in `<PROJECT-ROOT>/application.properties`
as followed:
```properties
OWM_API_KEY = <API Key for OpenWeatherMap>
RMV_API_KEY = <API Key for RMV-Auskunft-API>
```

Since the keys must not be shared this file will be ignored by GIT.
As a reference the file `<PROECT-ROOT>/template-application.properties` can be used.

## External Sources
The module `calendarView` is from [https://github.com/kizitonwose/CalendarView].

The icons with the prefix "`ic_`" are imported from [https://github.com/google/material-design-icons].
