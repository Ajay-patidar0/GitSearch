package com.example.gitsearch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.gitsearch.data.Repository
import com.example.gitsearch.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

@AndroidEntryPoint
class RepositoryActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val username = intent.getStringExtra("username") ?: ""
        if (username.isEmpty()) {
            setContent { DisplayErrorMessage("Please enter a valid username to proceed.") }
        } else {
            viewModel.fetchRepositories(username)
            setContent {
                RepositoryScreen(viewModel = viewModel, username = username) {
                    viewModel.fetchRepositories(username)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RepositoryScreen(viewModel: MainViewModel, username: String, onRefresh: () -> Unit) {
    val state = viewModel.state.collectAsState().value
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(SortOption.STARS) }
    var showSortMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    var isRefreshing by remember { mutableStateOf(false) }

    // Simplified background without expensive animations
    val gradientColors = listOf(
        Color(0xFFF8F9FA),
        Color(0xFFE8EAF6),
        Color(0xFFE3F2FD)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Repositories",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@$username",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                isRefreshing = true
                                onRefresh()
                                delay(1000)
                                isRefreshing = false
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White,
                            modifier = Modifier.rotate(if (isRefreshing) 360f else 0f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A73E8)
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(gradientColors)
                )
                .padding(padding)
        ) {
            when (state) {
                is MainViewModel.UiState.Loading -> {
                    LoadingScreen()
                }

                is MainViewModel.UiState.Error -> {
                    DisplayErrorMessage(state.message)
                }

                is MainViewModel.UiState.Success -> {
                    val filteredAndSortedRepos = remember(state.repos, searchQuery, sortBy) {
                        state.repos
                            .filter { repo ->
                                searchQuery.isEmpty() || repo.name.contains(searchQuery, ignoreCase = true) ||
                                        repo.description?.contains(searchQuery, ignoreCase = true) == true ||
                                        repo.language?.contains(searchQuery, ignoreCase = true) == true
                            }
                            .sortedWith(sortBy.comparator)
                    }

                    Column {
                        // Search and Filter Section
                        SearchAndFilterSection(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSearchClear = {
                                searchQuery = ""
                                keyboardController?.hide()
                            },
                            resultCount = filteredAndSortedRepos.size,
                            totalCount = state.repos.size
                        )

                        if (filteredAndSortedRepos.isEmpty()) {
                            if (searchQuery.isEmpty()) {
                                DisplayErrorMessage("No repositories found for \"$username\".")
                            } else {
                                NoSearchResultsMessage(searchQuery)
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = filteredAndSortedRepos,
                                    key = { it.name }
                                ) { repo ->
                                    val index = filteredAndSortedRepos.indexOf(repo)
                                    EnhancedRepositoryCard(
                                        repo = repo,
                                        modifier = Modifier.animateItemPlacement(
                                            animationSpec = tween(150)
                                        ),
                                        animationDelay = (index * 20).coerceAtMost(200)
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    DisplayErrorMessage("Something went wrong. Please try again.")
                }
            }

            // Sort Menu
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                modifier = Modifier.background(
                    Color.White,
                    RoundedCornerShape(12.dp)
                )
            ) {
                SortOption.values().forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    option.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (sortBy == option) Color(0xFF1A73E8) else Color.Gray
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    option.displayName,
                                    color = if (sortBy == option) Color(0xFF1A73E8) else Color.Black,
                                    fontWeight = if (sortBy == option) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        onClick = {
                            sortBy = option
                            showSortMenu = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndFilterSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    resultCount: Int,
    totalCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search repositories...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = onSearchClear) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { /* Handle search */ }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1A73E8),
                    cursorColor = Color(0xFF1A73E8)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) {
                        "$totalCount repositories"
                    } else {
                        "$resultCount of $totalCount repositories"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                if (searchQuery.isNotEmpty() && resultCount < totalCount) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Filtered") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF1A73E8).copy(alpha = 0.1f),
                            labelColor = Color(0xFF1A73E8)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color(0xFF1A73E8),
                strokeWidth = 4.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Loading repositories...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF1A73E8),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun EnhancedRepositoryCard(
    repo: Repository,
    modifier: Modifier = Modifier,
    animationDelay: Int = 0
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isVisible by remember { mutableStateOf(false) }

    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed) 12.dp else 6.dp,
        animationSpec = tween(100),
        label = "elevation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )

    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val intent = remember { Intent(Intent.ACTION_VIEW, repo.url.toUri()) }

    // Animation on first composition
    LaunchedEffect(repo) {
        delay(animationDelay.toLong())
        isVisible = true
    }

    if (showDialog) {
        EnhancedAlertDialog(
            repo = repo,
            onDismiss = { showDialog = false },
            onConfirm = {
                showDialog = false
                context.startActivity(intent)
            }
        )
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
            animationSpec = tween(200),
            initialOffsetY = { it / 4 }
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale
                )
                .shadow(animatedElevation, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { showDialog = true },
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header with name and open icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                Color(0xFF1A73E8).copy(alpha = 0.1f),
                                CircleShape
                            )
                            .clickable { showDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open Repository",
                            tint = Color(0xFF1A73E8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Statistics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Language chip
                    LanguageChip(repo.language)

                    // Stars
                    StatisticItem(
                        icon = Icons.Default.Star,
                        value = formatNumber(repo.stars),
                        color = Color(0xFFFFC107),
                        label = "stars"
                    )

                    // Add forks if available (assuming it's in the repository model)
                    // StatisticItem(
                    //     icon = Icons.Default.CallSplit,
                    //     value = formatNumber(repo.forks ?: 0),
                    //     color = Color(0xFF666666),
                    //     label = "forks"
                    // )
                }

                // Description
                repo.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF666666),
                            lineHeight = 20.sp
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageChip(language: String?) {
    if (language != null) {
        Box(
            modifier = Modifier
                .background(
                    getLanguageColor(language).copy(alpha = 0.15f),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(getLanguageColor(language), CircleShape)
                )
                Text(
                    text = language,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF333333)
                    )
                )
            }
        }
    }
}

@Composable
fun StatisticItem(
    icon: ImageVector,
    value: String,
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
        )
    }
}

@Composable
fun EnhancedAlertDialog(
    repo: Repository,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.github_icon), // Assuming you have a GitHub icon
                    contentDescription = "GitHub",
                    modifier = Modifier.size(32.dp),
                    tint = Color(0xFF1A1A1A)
                )
                Column {
                    Text(
                        "Open Repository",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        repo.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.Gray
                        )
                    )
                }
            }
        },
        text = {
            Column {
                Text("This will open the repository in your default browser.")

                repo.description?.let { desc ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.Gray
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A73E8)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF666666)
                )
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NoSearchResultsMessage(searchQuery: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = "No results",
                modifier = Modifier.size(80.dp),
                tint = Color.Gray.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No repositories found",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Try adjusting your search for \"$searchQuery\"",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun DisplayErrorMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFF5F5), Color(0xFFFFEBEE))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Color(0xFFFFEBEE),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        modifier = Modifier.size(40.dp),
                        tint = Color(0xFFD32F2F)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Oops! Something went wrong",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

enum class SortOption(
    val displayName: String,
    val icon: ImageVector,
    val comparator: Comparator<Repository>
) {
    STARS("Most Stars", Icons.Default.Star, compareByDescending { it.stars }),
    NAME("Name A-Z", Icons.Default.SortByAlpha, compareBy { it.name.lowercase() }),
    LANGUAGE("Language", Icons.Default.Code, compareBy { it.language ?: "zzz" })
}

fun formatNumber(number: Int): String {
    return when {
        number >= 1000000 -> String.format("%.1fM", number / 1000000.0)
        number >= 1000 -> String.format("%.1fK", number / 1000.0)
        else -> NumberFormat.getNumberInstance(Locale.getDefault()).format(number)
    }
}

fun getLanguageColor(language: String?): Color {
    return when (language?.lowercase()) {
        "kotlin" -> Color(0xFF7F52FF)
        "java" -> Color(0xFFB07219)
        "python" -> Color(0xFF3572A5)
        "javascript" -> Color(0xFFF1E05A)
        "typescript" -> Color(0xFF2B7489)
        "c" -> Color(0xFF555555)
        "c++" -> Color(0xFFF34B7D)
        "c#" -> Color(0xFF178600)
        "go" -> Color(0xFF00ADD8)
        "dart" -> Color(0xFF00B4AB)
        "swift" -> Color(0xFFFFAC45)
        "php" -> Color(0xFF4F5D95)
        "ruby" -> Color(0xFF701516)
        "rust" -> Color(0xFFDEA584)
        "shell" -> Color(0xFF89E051)
        "html" -> Color(0xFFE34C26)
        "css" -> Color(0xFF563D7C)
        "scss" -> Color(0xFFCD6799)
        "sql" -> Color(0xFF003B57)
        else -> Color(0xFFBDBDBD)
    }
}