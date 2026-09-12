# Reusable Module — Lớp dữ liệu (Repository / Retrofit / Room) cho Android Native

> Nạp file này khi màn hình cần **lấy dữ liệu từ mạng hoặc lưu trữ cục bộ**. Theo `build-sequences/android-native.md`, luôn code lớp này **TRƯỚC** giao diện (bottom-up).

## Nguyên tắc

- **Repository là cửa duy nhất** để lấy dữ liệu. ViewModel không bao giờ gọi thẳng Retrofit hay DAO.
- **Single Source of Truth (SSOT)** — mỗi loại dữ liệu chỉ có 1 nguồn chính xác. Với app offline-first, nguồn đó là Room (database), mạng chỉ để cập nhật vào Room.
- Mỗi Repository phụ trách **1 loại dữ liệu** (`ProductRepository`, `UserRepository`...), không gộp tất cả vào 1 file.

## Mẫu 1 — Bọc kết quả (Resource)

Dùng để chuyển lỗi mạng thành thứ UI hiển thị được, thay vì để app crash.

```kotlin
// ---------- core/common/Resource.kt ----------
sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>
    data object Loading : Resource<Nothing>
}

// Gọi API an toàn — KHÔNG bao giờ để exception thoát ra ngoài Repository
suspend fun <T> safeApiCall(apiCall: suspend () -> T): Resource<T> = try {
    Resource.Success(apiCall())
} catch (e: HttpException) {
    Resource.Error(
        when (e.code()) {
            401 -> "Phiên đăng nhập đã hết hạn"
            404 -> "Không tìm thấy dữ liệu"
            in 500..599 -> "Máy chủ đang gặp sự cố, thử lại sau"
            else -> "Lỗi kết nối (${e.code()})"
        },
        e,
    )
} catch (e: IOException) {
    Resource.Error("Không có kết nối mạng", e)
} catch (e: Exception) {
    Resource.Error(e.message ?: "Đã có lỗi xảy ra", e)
}
```

> Thông báo lỗi viết bằng **ngôn ngữ người dùng hiểu được**, không phải mã lỗi kỹ thuật — khớp với `user-profiles/non-dev.md`.

## Mẫu 2 — Repository offline-first

```kotlin
// ---------- core/data/ProductRepository.kt ----------
class ProductRepository(
    private val remote: ProductRemoteDataSource,
    private val local: ProductLocalDataSource,
) {
    // Trả dữ liệu cũ trong máy TRƯỚC (hiện ngay, không chờ mạng),
    // rồi mới cập nhật từ server.
    fun getProducts(): Flow<List<Product>> = flow {
        val cached = local.getAll().first()
        if (cached.isNotEmpty()) emit(cached)

        try {
            val fresh = remote.getProducts()
            local.replaceAll(fresh)
            emit(fresh)
        } catch (e: Exception) {
            // Có cache rồi thì lỗi mạng không sao — người dùng vẫn xem được
            if (cached.isEmpty()) throw e
        }
    }.flowOn(Dispatchers.IO)
}
```

## Mẫu 3 — Nguồn dữ liệu từ mạng (Retrofit)

```kotlin
// ---------- core/network/ProductApiService.kt ----------
interface ProductApiService {
    @GET("products")
    suspend fun getProducts(): List<ProductDto>

    @GET("products/{id}")
    suspend fun getProductById(@Path("id") id: String): ProductDto

    @POST("products")
    suspend fun createProduct(@Body body: CreateProductRequest): ProductDto

    @DELETE("products/{id}")
    suspend fun deleteProduct(@Path("id") id: String)
}

// DTO — hình dạng dữ liệu server trả về. TÁCH RIÊNG khỏi model dùng trong app.
data class ProductDto(val id: String, val name: String, val price: Long)

// Chuyển DTO → model của app
fun ProductDto.toDomain() = Product(id = id, name = name, price = price)

// ---------- core/data/ProductRemoteDataSource.kt ----------
class ProductRemoteDataSource(private val api: ProductApiService) {
    suspend fun getProducts(): List<Product> = api.getProducts().map { it.toDomain() }
}
```

**Quy tắc:** luôn tách **3 loại model** — `ProductDto` (mạng), `ProductEntity` (database), `Product` (dùng trong app). Không dùng chung 1 class cho cả 3, vì server đổi định dạng là hỏng toàn bộ app.

## Mẫu 4 — Lưu trữ cục bộ (Room)

```kotlin
// ---------- core/database/ProductEntity.kt ----------
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val price: Long,
)

// ---------- core/database/ProductDao.kt ----------
@Dao
interface ProductDao {
    @Query("SELECT * FROM products")
    fun getAll(): Flow<List<ProductEntity>>          // Flow: tự cập nhật khi DB đổi

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<ProductEntity>) {
        deleteAll()
        insertAll(items)
    }
}

// ---------- core/database/AppDatabase.kt ----------
@Database(entities = [ProductEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
}
```

**Quy tắc Room:**
- Hàm đọc trả `Flow<...>` (tự cập nhật), hàm ghi là `suspend`.
- Xoá-rồi-thêm phải bọc trong `@Transaction` — nếu không, lỗi giữa chừng làm mất sạch dữ liệu.
- Giữ `exportSchema = true` để lần sau đổi cấu trúc DB còn viết được migration.
- Đổi cấu trúc bảng thì **bắt buộc tăng `version`** và viết migration — nếu không app sẽ crash ở máy người đã cài bản cũ.

## Mẫu 5 — Lưu cấu hình nhỏ (DataStore)

Dùng cho tuỳ chọn người dùng (chế độ tối, đã xem hướng dẫn chưa...). **Không dùng `SharedPreferences`** — đã lỗi thời.

```kotlin
private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsDataSource(private val context: Context) {
    private val darkModeKey = booleanPreferencesKey("dark_mode")

    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[darkModeKey] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[darkModeKey] = enabled }
    }
}
```

⚠️ **Không lưu mật khẩu / token vào DataStore** — dùng `EncryptedSharedPreferences` hoặc Android Keystore. Xem `references/privacy-security.md` trong skill `android-native-dev`.

## Thứ tự code (bottom-up — bắt buộc)

```
1. Model (Product)  →  2. DTO + Entity  →  3. DAO + ApiService
       →  4. DataSource  →  5. Repository  →  6. ViewModel  →  7. UI
```

Lý do: viết giao diện trước rồi mới nghĩ dữ liệu sẽ phải sửa lại giao diện nhiều lần. Khi chưa có API thật, tạo **dữ liệu giả (mock)** ở bước 5 để bước 6-7 chạy được ngay:

```kotlin
class FakeProductRepository : ProductRepository {
    override fun getProducts(): Flow<List<Product>> = flowOf(
        listOf(Product("1", "Sản phẩm mẫu", 100_000))
    )
}
```
