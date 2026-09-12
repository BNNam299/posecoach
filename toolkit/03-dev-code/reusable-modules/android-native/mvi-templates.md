# Reusable Module — Mẫu UI & ViewModel (MVI) cho Android Native

> Nạp file này khi code **bất kỳ màn hình nào**. Đây là khuôn mẫu bắt buộc — không tự nghĩ cấu trúc khác.

## Nguyên tắc: Luồng dữ liệu một chiều (UDF)

```
ViewModel ──── State (bất biến) ────► UI Screen
    ▲                                      │
    └──────────── Action ──────────────────┘
```

- **State** chảy xuống UI, luôn **immutable**.
- **Action** chảy lên ViewModel — mọi thao tác người dùng đều là 1 Action.
- ViewModel đổi State → Compose tự recompose. **UI không bao giờ tự sửa State.**

## Mẫu 1 — Bộ 3 file cho mỗi màn hình

Mỗi màn hình luôn có đúng 3 khái niệm: `UiState` (hiển thị gì), `Action` (người dùng làm gì), `ViewModel` (xử lý).

```kotlin
// ---------- <Ten>UiState.kt ----------
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val items: List<Product>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

// ---------- <Ten>Action.kt ----------
sealed interface HomeAction {
    data object Load : HomeAction
    data object Refresh : HomeAction
    data class ProductClicked(val id: String) : HomeAction
}
```

> Dùng `sealed interface` + `data object` (không phải `sealed class` + `object`) — đây là cú pháp Kotlin hiện hành.

## Mẫu 2 — ViewModel

```kotlin
// ---------- HomeViewModel.kt ----------
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // MỘT cửa vào duy nhất cho mọi thao tác của người dùng
    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Load, HomeAction.Refresh -> load()
            is HomeAction.ProductClicked -> { /* phát sự kiện điều hướng */ }
        }
    }

    private fun load() {
        viewModelScope.launch {
            repository.getProducts()
                .onStart { _uiState.value = HomeUiState.Loading }
                .catch { e -> _uiState.value = HomeUiState.Error(e.message ?: "Đã có lỗi xảy ra") }
                .collect { items -> _uiState.value = HomeUiState.Success(items) }
        }
    }
}
```

**Quy tắc ViewModel:**
1. `MutableStateFlow` **private**, `StateFlow` public qua `.asStateFlow()` — không bao giờ để UI ghi được State.
2. Chỉ có **1 hàm public `onAction()`** — không tạo `loadData()`, `refresh()`, `onClick()` public riêng lẻ.
3. **Bắt lỗi trong ViewModel**, không đẩy exception lên UI.
4. **Không gọi thẳng Retrofit/DAO** — luôn qua Repository.
5. Không giữ tham chiếu tới Context/View/Composable.

## Mẫu 3 — Composable (tách Stateful / Stateless)

Luôn tách làm 2 tầng. Tầng ngoài nối ViewModel, tầng trong thuần UI (nhờ vậy preview và test được).

```kotlin
// ---------- HomeScreen.kt ----------

// Tầng ngoài: nối ViewModel — KHÔNG chứa UI
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(uiState = uiState, onAction = viewModel::onAction)
}

// Tầng trong: thuần UI — KHÔNG biết ViewModel là gì
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        HomeUiState.Loading -> LoadingIndicator(modifier)
        is HomeUiState.Success -> ProductList(
            items = uiState.items,
            onItemClick = { onAction(HomeAction.ProductClicked(it)) },
            modifier = modifier,
        )
        is HomeUiState.Error -> ErrorMessage(uiState.message, onRetry = { onAction(HomeAction.Refresh) })
    }
}

// Component tái sử dụng: luôn nhận modifier làm tham số CUỐI có giá trị mặc định
@Composable
fun ProductItem(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) { /* ... */ }
```

**Quy tắc Compose:**
1. Luôn dùng `collectAsStateWithLifecycle()` — **không dùng** `collectAsState()` (không tự dừng khi app xuống nền → tốn pin).
2. Composable tái sử dụng luôn có `modifier: Modifier = Modifier` là **tham số cuối cùng**.
3. Không đọc state toàn cục trong Composable — mọi thứ truyền qua tham số.
4. Không viết logic nghiệp vụ trong Composable.
5. Với danh sách: dùng `LazyColumn` (không phải `Column` + `forEach`), và **luôn truyền `key`**:
   ```kotlin
   items(items = products, key = { it.id }) { ProductItem(it, onClick = {}) }
   ```

## Mẫu 4 — Tối ưu recompose

```kotlin
// remember: cache phép tính tốn kém, chỉ chạy lại khi tham số đổi
val filtered = remember(items, query) { items.filter { it.name.contains(query) } }

// derivedStateOf: chỉ recompose khi GIÁ TRỊ SUY RA đổi, không phải khi nguồn đổi
val showScrollToTop by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 0 }
}
```

**Chỉ tối ưu khi đã thấy giật/lag thật.** Trước khi tối ưu, nạp skill `compose-performance` để đo — không đoán.

## Mẫu 5 — Dependency Injection (Hilt)

Toolkit này chốt dùng **Hilt** (xem lý do trong `tech-stacks/android-native.md`). Chỉ cần 3 chỗ:

```kotlin
// ---------- 1. Application: đánh dấu 1 lần cho cả app ----------
@HiltAndroidApp
class MyApplication : Application()

// ---------- 2. MainActivity ----------
@AndroidEntryPoint
class MainActivity : ComponentActivity() { /* ... */ }

// ---------- 3. Module: dạy Hilt cách tạo những thứ không tự tạo được ----------
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)     // đọc từ config, KHÔNG viết cứng URL vào code
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideProductApi(retrofit: Retrofit): ProductApiService =
        retrofit.create(ProductApiService::class.java)
}
```

Class **do mình viết** (Repository, DataSource) thì không cần `@Provides` — chỉ cần thêm `@Inject constructor`:

```kotlin
class ProductRepository @Inject constructor(
    private val remote: ProductRemoteDataSource,
    private val local: ProductLocalDataSource,
)
```

> **Quy tắc quan trọng:** nếu Hilt báo lỗi lúc build (kiểu "cannot be provided without an @Provides-annotated method"), nghĩa là quên khai báo 1 thành phần. **Đây là điểm mạnh, không phải phiền toái** — lỗi hiện lúc build để sửa ngay, thay vì crash trên điện thoại người dùng.

⚠️ Đừng nhầm với Koin (`koinViewModel()`, `single { }`, `startKoin { }`) — nhiều hướng dẫn trên mạng dùng Koin. Dự án này dùng Hilt.
