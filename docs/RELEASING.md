# Выпуск релиза (для владельца)

Ключ подписи и пароли хранит только владелец: в репозиторий они не попадают (`*.jks`, `*.keystore` и
`keystore.properties` в `.gitignore`). Релиз состоит из одного файла, универсального APK.

## 1. Ключ (один раз)

```bash
keytool -genkeypair -v -keystore ~/wuzzzik-release.jks -alias wuzzzik \
  -keyalg RSA -keysize 4096 -validity 10000
```

Файл `~/wuzzzik-release.jks` храните вне репозитория, сделайте его копию и запомните пароли: потеряв ключ, вы не
сможете обновлять уже установленное приложение. Имя в сертификате (CN, O) увидят все, кто смотрит свойства APK:
не вводите там личные данные, которыми не готовы делиться.

## 2. keystore.properties (один раз)

В корне проекта создайте `keystore.properties` (в git он не попадёт):

```properties
storeFile=/Users/<вы>/wuzzzik-release.jks
storePassword=...
keyAlias=wuzzzik
keyPassword=...
```

## 3. Сборка

```bash
./gradlew clean assembleRelease
```

Берём `app/build/outputs/apk/release/app-universal-release.apk` (подписанный). Файлы `app-arm64-v8a-*` и
`app-armeabi-v7a-*` в релиз не кладём.

## 4. Проверка

```bash
$ANDROID_HOME/build-tools/<версия>/apksigner verify --verbose --print-certs app-universal-release.apk
adb install -r app-universal-release.apk
```

Проверьте на телефоне: запуск, сканирование, воспроизведение, запись тега, футер в настройках показывает `1.0-start`.

## 5. Публикация на GitHub

```bash
cp app/build/outputs/apk/release/app-universal-release.apk WUZZZIK-1.0-start.apk
git tag v1.0-start && git push origin v1.0-start
gh release create v1.0-start WUZZZIK-1.0-start.apk \
  --title "WUZZZIK 1.0-start" --notes-file docs/release-notes-1.0-start.md
```

Обычный релиз (не pre-release): флаг `--prerelease` не указываем. GitHub сам добавляет к релизу архивы исходников
(`Source code`), отключить это нельзя; других файлов не загружаем.
