package io.github.jwyoon1220.config

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class YamlConfiguration {
    private var data: MutableMap<String, Any> = Object2ObjectOpenHashMap()
    private val yaml: Yaml

    init {
        // YAML 파일이 보기 좋게 저장되도록 옵션 설정 (Block 스타일)
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
        }
        yaml = Yaml(options)
    }

    /**
     * YAML 파일에서 데이터를 불러옵니다.
     */
    fun load(file: File) {
        if (!file.exists()) return
        FileReader(file).use { reader ->
            val loaded = yaml.load<Map<String, Any>>(reader)
            if (loaded != null) {
                data = loaded.toMutableMap()
            } else {
                data.clear()
            }
        }
    }

    /**
     * 데이터를 YAML 파일로 저장합니다.
     */
    fun save(file: File) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        FileWriter(file).use { writer ->
            yaml.dump(data, writer)
        }
    }

    /**
     * "path.to.key" 형태의 경로를 통해 값을 가져옵니다.
     */
    fun get(path: String): Any? {
        if (path.isEmpty()) return data

        val keys = path.split(".")
        var current: Any? = data

        for (key in keys) {
            if (current is Map<*, *>) {
                current = current[key]
            } else {
                return null
            }
        }
        return current
    }

    /**
     * "path.to.key" 형태의 경로에 값을 설정합니다. (null 입력 시 삭제)
     */
    fun set(path: String, value: Any?) {
        if (path.isEmpty()) return

        val keys = path.split(".")
        var current: MutableMap<String, Any> = data

        // 마지막 키 이전까지의 맵 구조를 탐색 및 생성
        for (i in 0 until keys.size - 1) {
            val key = keys[i]
            if (!current.containsKey(key) || current[key] !is MutableMap<*, *>) {
                current[key] = mutableMapOf<String, Any>()
            }
            @Suppress("UNCHECKED_CAST")
            current = current[key] as MutableMap<String, Any>
        }

        val lastKey = keys.last()
        if (value == null) {
            current.remove(lastKey)
        } else {
            current[lastKey] = value
        }
    }

    // 편의를 위한 타입별 Getter 메서드들
    fun getString(path: String): String? = get(path)?.toString()
    fun getInt(path: String): Int? = (get(path) as? Number)?.toInt()
    fun getDouble(path: String): Double? = (get(path) as? Number)?.toDouble()
    fun getBoolean(path: String): Boolean = get(path) as? Boolean ?: false

    @Suppress("UNCHECKED_CAST")
    fun getStringList(path: String): List<String> {
        val list = get(path) as? List<*> ?: return emptyList()
        return list.map { it.toString() }
    }
}