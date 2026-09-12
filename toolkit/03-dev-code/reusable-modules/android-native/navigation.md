# Reusable Module — Điều hướng (Navigation Compose) cho Android Native

> Nạp file này ở **Phase 2** (dựng khung điều hướng) và mỗi khi thêm màn hình mới.

## Vì sao dùng Navigation 2 chứ không phải Navigation 3

Navigation 3 đã stable và Google khuyến nghị cho app Compose mới, **nhưng không skill nào trong toolkit này biết nó** (đã kiểm tra cả 12 skill). Vì toolkit dựa vào skill để sinh code đúng, dùng Nav 2 an toàn hơn. Xem lý do đầy đủ trong `tech-stacks/android-native.md`.

⚠️ **Không nhầm với Navigation 3.** Nếu thấy `NavDisplay`, `rememberNavBackStack`, `entryProvider` — đó là API Navigation 3, **không dùng** trong dự án này.

## Route type-safe (bắt buộc — không dùng chuỗi)

Rất nhiều hướng dẫn trên mạng viết route dạng chuỗi `composable("detail/{id}")`. **Đó là cách cũ.** Từ Navigation 2.8, Google khuyến nghị route type-safe bằng `@Serializable` — gõ sai tên màn hình hoặc sai kiểu tham số sẽ **báo lỗi lúc build** thay vì crash lúc chạy.

### Bước 1 — Khai báo màn hình

```kotlin
// ---------- navigation/Routes.kt ----------
import kotlinx.serialization.Serializable

@Serializable
data object Home                                  // màn hình không có tham số

@Serializable
data class ProductDetail(val productId: String)   // màn hình có tham số

@Serializable
data object Settings
```

> Cần plugin `kotlinx-serialization` trong `build.gradle.kts` và khai báo trong `libs.versions.toml`.

### Bước 2 — Dựng sơ đồ điều hướng

```kotlin
// ---------- navigation/AppNavHost.kt ----------
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = modifier,
    ) {
        composable<Home> {
            HomeScreen(
                onProductClick = { id -> navController.navigate(ProductDetail(id)) },
                onSettingsClick = { navController.navigate(Settings) },
            )
        }

        composable<ProductDetail> { backStackEntry ->
            val route: ProductDetail = backStackEntry.toRoute()
            ProductDetailScreen(productId = route.productId)
        }

        composable<Settings> { SettingsScreen() }
    }
}
```

### Bước 3 — Đọc tham số trong ViewModel (cách tốt hơn)

Thay vì truyền `productId` qua nhiều tầng, ViewModel tự đọc từ `SavedStateHandle` — nhờ vậy Composable không cần biết gì về điều hướng:

```kotlin
@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProductRepository,
) : ViewModel() {
    private val route: ProductDetail = savedStateHandle.toRoute()
    private val productId = route.productId
    // ...
}
```

## Quy tắc điều hướng

1. **Màn hình không tự điều hướng.** Composable nhận callback (`onProductClick: (String) -> Unit`), `AppNavHost` mới là nơi gọi `navController.navigate(...)`. Nhờ vậy màn hình test và preview được độc lập.
2. **Không truyền object lớn qua route** — chỉ truyền `id` (chuỗi/số), rồi để ViewModel tự lấy dữ liệu đầy đủ từ Repository. Route bị giới hạn dung lượng và không lưu được ảnh/danh sách.
3. **Không truyền `navController` xuống các Composable con.** Chỉ `AppNavHost` giữ nó.
4. Cần điều hướng từ ViewModel (ví dụ đăng nhập xong thì chuyển trang) → phát sự kiện một lần bằng `Channel`/`SharedFlow`, không gọi `navigate` trực tiếp trong ViewModel.

## Mẫu điều hướng có thanh tab dưới (Bottom Navigation)

```kotlin
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevelRoutes.forEach { item ->
                    val selected = backStackEntry?.destination?.hierarchy
                        ?.any { it.hasRoute(item.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true   // bấm lại tab không tạo màn hình trùng
                                restoreState = true      // quay lại tab giữ nguyên vị trí cuộn
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        AppNavHost(navController = navController, modifier = Modifier.padding(padding))
    }
}
```

> 3 tuỳ chọn `popUpTo` + `launchSingleTop` + `restoreState` là **bắt buộc** cho bottom nav. Thiếu chúng, bấm tab qua lại nhiều lần sẽ chồng màn hình lên nhau và nút Back phải bấm hàng chục lần mới thoát — đây là lỗi kinh điển của Android.

## Kiểm tra ở Phase 2

Sau khi dựng xong khung, tự kiểm 4 điểm trước khi báo người dùng:

- [ ] Bấm qua lại giữa mọi màn hình được
- [ ] Nút **Back** vật lý quay về đúng màn hình trước, không thoát app đột ngột
- [ ] Xoay ngang/dọc → vẫn ở đúng màn hình, không nhảy về đầu
- [ ] Bấm tab đang đứng nhiều lần → không chồng màn hình (kiểm tra bằng cách bấm Back)
