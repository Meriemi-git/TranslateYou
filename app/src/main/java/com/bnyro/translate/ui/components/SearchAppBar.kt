package com.bnyro.translate.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bnyro.translate.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    var isSearchViewVisible by remember {
        mutableStateOf(false)
    }

    TopAppBar(
        navigationIcon = {
            if (!isSearchViewVisible) navigationIcon()
        },
        title = {
            Text(title)
        },
        actions = {
            Crossfade(isSearchViewVisible) {
                when (it) {
                    true -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 15.dp)
                        ) {
                            val focusManager = LocalFocusManager.current
                            val focusRequester = remember { FocusRequester() }

                            SideEffect {
                                focusRequester.requestFocus()
                            }
                            StyledIconButton(
                                modifier = Modifier.padding(top = 10.dp),
                                imageVector = Icons.Default.ArrowBack
                            ) {
                                isSearchViewVisible = false
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedTextField(
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .weight(1f),
                                value = value,
                                onValueChange = onValueChange,
                                label = {
                                    Text(text = stringResource(R.string.search))
                                },
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                    }
                                ),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.Search, null)
                                }
                            )
                        }
                    }
                    else -> {
                        StyledIconButton(imageVector = Icons.Default.Search) {
                            isSearchViewVisible = true
                        }
                        actions.invoke(this)
                    }
                }
            }
        }
    )
}
