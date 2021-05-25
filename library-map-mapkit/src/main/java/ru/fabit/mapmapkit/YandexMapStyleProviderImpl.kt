package ru.fabit.mapmapkit

import ru.fabit.map.dependencies.provider.MapStyleProvider

/**
 * Функция которая задает стиль карты
 *
 * @param featureType - Класс объекта, к которому нужно применить стили.
 *                      Чтобы применить стили ко всем объектам на слое, используйте значение all
 * @param hue         - Изменяет цветовой тон объектов на карте. Допустимые значения: от -1 до 1
 *                      По умолчанию 0
 * @param saturation  - Изменяет цветовой тон объектов на карте. Допустимые значения: от -1 до 1
 *                      По умолчанию 0
 * @param lightness   - Изменяет яркость цветов на карте. Допустимые значения: от -1 до 1.
 *                      По умолчанию 0.25
 */

class YandexMapStyleProviderImpl(
    private val saturation: Float? = -0.75f, private val lightness: Float? = 0.25f, private val defaultSaturation: Float? = 0f
): MapStyleProvider {

    override fun getDefaultStyle(): String {
        return "[" +
                "  {" +
                "    \"stylers\" : {" +
                "      \"saturation\" : $defaultSaturation," +
                "      \"lightness\" : $lightness" +
                "    }" +
                "  }" +
                "]"
    }

    override fun getCongestionStyle(): String {
        return "[" +
                "  {" +
                "    \"stylers\" : {" +
                "      \"saturation\" : $saturation," +
                "      \"lightness\" : $lightness" +
                "    }" +
                "  }" +
                "]"
    }
}