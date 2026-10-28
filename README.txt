WII LAUNCHER - Xiaomi TV
========================
1) COMPILER (une fois, sur PC)
   - Ouvre le dossier WiiLauncher dans Android Studio, laisse-le synchroniser Gradle.
   - Menu Build > Build APK(s)   (ou en terminal : gradlew assembleRelease)
   - APK : app/build/outputs/apk/release/app-release.apk

2) INSTALLER via adb
   adb connect IP_DE_LA_TV:5555
   adb install -r app-release.apk
   adb shell am start -n com.wii.launcher/.MainActivity

3) EN FAIRE L'ECRAN D'ACCUEIL (touche Home)
   adb shell cmd package set-home-activity com.wii.launcher/.MainActivity
   Si la TV continue d'ouvrir son launcher d'origine :
   adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
   (note le package du launcher Xiaomi) puis :
   adb shell pm disable-user --user 0 <package_du_launcher_xiaomi>
   Pour annuler : adb shell pm enable <package>   /   adb uninstall com.wii.launcher

Commandes : flèches = naviguer, OK = lancer, CH+/CH- ou Page +/- = changer de page.
