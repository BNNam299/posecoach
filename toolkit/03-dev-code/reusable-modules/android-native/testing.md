# Reusable Module — Kiểm thử (Testing) cho Android Native

> Nạp khi cần viết test. Với người **không phải dev**: test là cách để sửa 1 chỗ mà không vô tình làm hỏng chỗ khác — càng nhiều màn hình thì càng đáng làm.

## Hai loại test và khi nào dùng

| Loại | Chạy ở đâu | Tốc độ | Dùng để |
|---|---|---|---|
| **Unit test** (`test/`) | Máy tính, không cần điện thoại | Rất nhanh (giây) | Kiểm tra ViewModel, Repository — phần **logic** |
| **UI test** (`androidTest/`) | Máy ảo hoặc máy thật | Chậm (phút) | Kiểm tra màn hình hiện đúng — phần **giao diện** |

**Ưu tiên Unit test.** Chỉ viết UI test cho những luồng quan trọng nhất (đăng nhập, thanh toán) — nhiều UI test làm build chậm mà ít giá trị.

## Thư viện

```toml
# gradle/libs.versions.toml
junit = "4.13.2"
mockk = "1.13.13"          # tạo đối tượng giả
turbine = "1.2.0"          # test Flow/StateFlow
coroutines-test = "1.9.0"  # điều khiển thời gian trong coroutine
```

## Mẫu 1 — Quy tắc bắt buộc cho test ViewModel

ViewModel dùng `Dispatchers.Main` (luồng giao diện) — thứ không tồn tại khi chạy test trên máy tính. Không có lớp này thì **mọi test ViewModel đều lỗi**:

```kotlin
// ---------- MainDispatcherRule.kt ----------
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

## Mẫu 2 — Test ViewModel

```kotlin
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: ProductRepository = mockk()
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() { viewModel = HomeViewModel(repository) }

    @Test
    fun `tai du lieu thanh cong thi hien danh sach`() = runTest {
        // Given — giả lập repository trả về dữ liệu
        val products = listOf(Product("1", "Áo thun", 150_000))
        every { repository.getProducts() } returns flowOf(products)

        // When — người dùng mở màn hình
        viewModel.onAction(HomeAction.Load)

        // Then — state chuyển thành Success với đúng dữ liệu
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is HomeUiState.Success)
            assertEquals(products, (state as HomeUiState.Success).items)
        }
    }

    @Test
    fun `mat mang thi hien thong bao loi`() = runTest {
        every { repository.getProducts() } returns flow { throw IOException("offline") }

        viewModel.onAction(HomeAction.Load)

        viewModel.uiState.test {
            assertTrue(awaitItem() is HomeUiState.Error)
        }
    }
}
```

**Cấu trúc Given / When / Then** — luôn viết theo 3 phần này, tên test đặt bằng câu mô tả hành vi trong dấu backtick.

## Mẫu 3 — Test giao diện Compose

Test tầng **Content** (stateless), không phải tầng Screen — vì tầng Content không cần ViewModel:

```kotlin
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dangTai_thiHienVongQuay() {
        composeTestRule.setContent {
            HomeContent(uiState = HomeUiState.Loading, onAction = {})
        }
        composeTestRule.onNodeWithTag("loading").assertIsDisplayed()
    }

    @Test
    fun bamSanPham_thiPhatRaAction() {
        var captured: HomeAction? = null
        composeTestRule.setContent {
            HomeContent(
                uiState = HomeUiState.Success(listOf(Product("1", "Áo thun", 150_000))),
                onAction = { captured = it },
            )
        }
        composeTestRule.onNodeWithText("Áo thun").performClick()
        assertEquals(HomeAction.ProductClicked("1"), captured)
    }
}
```

> Đây chính là lý do Mẫu 3 của `mvi-templates.md` tách Screen thành 2 tầng — tầng Content test được mà không cần dựng cả ViewModel.

Cần pattern sâu hơn (đồng bộ, animation, danh sách cuộn): nạp skill `compose-ui-testing-patterns`.

## Lệnh chạy test

```powershell
.\gradlew testDebugUnitTest              # unit test — nhanh, chạy thường xuyên
.\gradlew connectedDebugAndroidTest      # UI test — cần máy ảo/máy thật đang bật
.\gradlew test                           # tất cả unit test
```

## Kiểm tra chất lượng code

```powershell
.\gradlew lint                           # Android Lint — có sẵn, không cần cài
```

`ktlint` / `detekt` là tuỳ chọn thêm — **hỏi người dùng trước khi thêm** vào dự án, vì chúng làm build chậm hơn và sinh nhiều cảnh báo có thể gây hoang mang cho người không chuyên.

## Khi nào viết test (áp dụng trong `build-sequences/android-native.md`)

- **Phase 3** (mỗi màn hình): viết unit test cho ViewModel của màn hình đó nếu nó có xử lý logic (gọi API, lọc, tính toán). Màn hình chỉ hiển thị tĩnh thì bỏ qua.
- **Phase 4** (hoàn thiện): chạy `.\gradlew testDebugUnitTest` + `.\gradlew lint` trước khi review code.
- Sửa xong 1 lỗi → viết 1 test tái hiện lỗi đó, để nó không quay lại. Ghi song song vào `FOOTGUNS.md`.
