# Gifts 2.0: аудит подарочных сообщений в чате (Этап A, #1142)

Документ фиксирует факты по текущему DEV и поставляемому `tdlib-java.jar` (1.8.65). Код не менялся. Матрица типов —
ниже; на её основе планируются Этапы B–E.

## 1. Что есть в текущей библиотеке (проверено `javap`)

Семейство подарочных сообщений (`TdApi.MessageContent`):

| Класс | Назначение | Ключевые поля |
| --- | --- | --- |
| `MessageGift` | обычный подарок (в т.ч. уникализированный later) | `gift: Gift`, `senderId`, `receiverId`, `receivedGiftId`, `text: FormattedText` (комментарий), `uniqueGiftNumber`, `sellStarCount`, `prepaidUpgradeStarCount`, `isUpgradeSeparate`, `isFromAuction`, `isPrivate`, `isSaved`, `isPrepaidUpgrade`, `canBeUpgraded`, `wasConverted`, `wasUpgraded`, `wasRefunded`, `upgradedReceivedGiftId`, `prepaidUpgradeHash` |
| `MessageUpgradedGift` | коллекционный/уникальный подарок | `gift: UpgradedGift`, `senderId`, `receiverId`, `origin: UpgradedGiftOrigin`, `receivedGiftId`, `isSaved`, `canBeTransferred`, `wasTransferred`, `transferStarCount`, `dropOriginalDetailsStarCount`, `nextTransferDate`, `nextResaleDate`, `exportDate`, `craftDate` |
| `MessageRefundedUpgradedGift` | возврат коллекционного подарка | `gift: Gift`, `senderId`, `receiverId`, `origin` |
| `MessageUpgradedGiftPurchaseOffer` | предложение о покупке подарка | `gift: UpgradedGift`, `state: GiftPurchaseOfferState`, `price: GiftResalePrice`, `expirationDate` |
| `MessageUpgradedGiftPurchaseOfferRejected` | отказ/истечение предложения | `gift: UpgradedGift`, `price`, `offerMessageId`, `wasExpired` |
| `MessageGiftedPremium` | подарок Premium | `gifterUserId`, `receiverUserId`, `text`, `currency`, `amount`, `cryptocurrency`, `cryptocurrencyAmount`, `monthCount`, `dayCount`, `sticker` |
| `MessageGiftedStars` | подарок Stars | `gifterUserId`, `receiverUserId`, `currency`, `amount`, `cryptocurrency`, `cryptocurrencyAmount`, `starCount`, `transactionId`, `sticker` |
| `MessageGiftedTon` | подарок TON | `gifterUserId`, `receiverUserId`, `tonAmount`, `transactionId`, `sticker` |

Модели содержимого:

- `Gift`: `id`, `publisherChatId`, `sticker: Sticker`, `starCount`, `defaultSellStarCount`, `upgradeStarCount`,
  `upgradeVariantCount`, `hasColors`, `isForBirthday`, `isPremium`, `auctionInfo`, `nextSendDate`, `userLimits`,
  `overallLimits`, `background: GiftBackground`, `firstSendDate`, `lastSendDate`.
- `UpgradedGift`: `id`, `regularGiftId`, `publisherChatId`, `title`, `name`, `number`, `totalUpgradedCount`,
  `maxUpgradedCount`, `isBurned`, `isCrafted`, `isPremium`, `isThemeAvailable`, `usedThemeChatId`, `hostId`, `ownerId`,
  `ownerAddress`, `ownerName`, `giftAddress`, `model: UpgradedGiftModel`, `symbol: UpgradedGiftSymbol`,
  `backdrop: UpgradedGiftBackdrop`, `originalDetails`, `colors`, `resaleParameters`, `canSendPurchaseOffer`,
  `craftProbabilityPerMille`, `valueCurrency`, `valueAmount`, `valueUsdAmount`.
- `SentGiftRegular` / `SentGiftUpgraded`, `ReceivedGift`, `GiftBackground`, `UpgradedGiftModel/Symbol/Backdrop/Colors`
  доступны для повторного использования в визуальном слое.

«Подарка в валюте TON» как отдельного предметного объекта нет: `MessageGiftedTon` — денежный перевод со стикером.

## 2. Текущее поведение DEV

`TdServiceMessageMapper` превращает **все** подарочные сообщения в текст:

- `MessageGift` → `giftText(...)`: «подарок отправлен/получен», звёзды, комментарий; возврат и конвертация — отдельные
  строки, но без модели, номера и фона.
- `MessageUpgradedGift` → строка «коллекционный подарок» (`service_*`), без названия модели, номера и редкости.
- `MessageGiftedPremium`, `MessageGiftedStars`, `MessageGiftedTon` → текстовые строки; валюта и стикер не показываются.

Следствие: в `ChatScreen` для них используется общий `ServiceMessageChip`; структурированные данные (стикер, фон,
символ, номер, модель, редкость) теряются в маппинге до UI. `GiftVisual`/`GiftCollectibleDetails` уже существуют в
`domain/model/Monetization.kt` и используются в магазине и профиле, но не на пути сообщений.

## 3. Матрица типов (целевое состояние)

| Тип | Класс TDLib | Входящий | Исходящий | Что показывать | Действие | Fallback |
| --- | --- | --- | --- | --- | --- | --- |
| Обычный подарок | `MessageGift`, `wasRefunded=false`, `wasConverted=false` | да | да | стикер `gift.sticker`, комментарий, звёзды | просмотр (если есть), «детали» | текст-строка текущего вида |
| Конвертирован в звёзды | `MessageGift.wasConverted` | да | да | сумма `sellStarCount`, без стикера предмета | нет | текст «конвертирован» |
| Возвращён | `MessageGift.wasRefunded`, `MessageRefundedUpgradedGift` | да | да | состояние «возвращён», исходный стикер при наличии | нет | текст «возвращён» |
| Уникальный/коллекционный | `MessageUpgradedGift` | да | да | модель, символ, фон, название, номер (`number`), редкость (`backdrop`/`model`), `title` | открыть детали при подтверждённой навигации | текст + стикер обычного подарка |
| Предложение о покупке | `MessageUpgradedGiftPurchaseOffer` | да | да | цена `GiftResalePrice`, состояние, срок | нет (платёж не трогаем) | текст |
| Отказ/истечение предложения | `...PurchaseOfferRejected` | да | да | цена, `wasExpired` | нет | текст |
| Premium | `MessageGiftedPremium` | да | да | стикер, месяцы/дни, сумма | нет | текст |
| Stars | `MessageGiftedStars` | да | да | стикер, `starCount` | нет | текст |
| TON | `MessageGiftedTon` | да | да | стикер, `tonAmount` | нет | текст |

Правила: валютные подарки (Premium/Stars/TON) **никогда** не рендерятся как коллекционные предметы; отсутствующие
ресурсы дают статичный fallback; комментарий (`text`) сохраняется; `uniqueGiftNumber` показывается только когда
`> 0`; ничего не выдумывается при пустых полях.

## 4. Что нужно сделать (этапы)

- **B.** Расширить домен сообщения: хранить `GiftChatContent` (regular/upgraded/currency/offer/refund) рядом с
  текстовым представлением для поиска и доступности. Переиспользовать `GiftVisual`/`GiftCollectibleDetails`,
  сохранить реальные `fileId` стикера/фона/модели. Не менять маппинг остальных служебных сообщений.
- **C.** Специализированная карточка в ленте вместо `ServiceMessageChip` для предметных подарков; общие компоненты
  Gifts 2.0 (`GiftVisualImage`, `GiftCollectibleBackdrop`, паттерн), без своих загрузчиков и кэшей.
- **D.** Та же композиция в предпросмотре отправки; без фиктивных сообщений в реальном чате; навигация — только при
  наличии подтверждённого экрана (#1143), иначе безопасный fallback.
- **E.** Бюджет анимаций, lifecycle, отмена устаревших запросов, отсутствие роста памяти; скриншот-гейты Roborazzi.

## 5. Ограничения

- TDLib не обновляется, платёжные операции не выполняются, BG Media/музыка/Profile/Gifts-магазин не меняются без
  согласования с Codex.
- Синтетические скриншоты не доказывают работу живого серверного API.
- Перед Этапом B согласовать расширение общих моделей с Codex в #1142/#1204.