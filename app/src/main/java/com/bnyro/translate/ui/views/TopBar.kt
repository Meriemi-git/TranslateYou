package com.bnyro.translate.ui.views

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bnyro.translate.R
import com.bnyro.translate.obj.MenuItemData
import com.bnyro.translate.ui.components.StyledIconButton
import com.bnyro.translate.ui.components.TopBarMenu
import com.bnyro.translate.ui.models.TranslationModel
import com.bnyro.translate.util.ClipboardHelper
import com.bnyro.translate.util.ComposeFileProvider
import com.bnyro.translate.util.SpeechHelper
import com.bnyro.translate.util.TessHelper
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    mainModel: TranslationModel,
    menuItems: List<MenuItemData>
) {

    var hasImage by remember {
        mutableStateOf(false)
    }

    var imageUri by remember {
        mutableStateOf<Uri?>(null)
    }

    val context = LocalContext.current
    val handler = Handler(Looper.getMainLooper())
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            hasImage = true
            mainModel.processImage(context,  result.uriContent)
        }
    }
    TopAppBar(
        title = {
            Text(
                text = stringResource(id = R.string.app_name)
            )
        },
        actions = {

            if (mainModel.insertedText == "" && SpeechRecognizer.isRecognitionAvailable(context)) {

                StyledIconButton(
                    imageVector = Icons.Default.Mic
                ) {
                    if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        SpeechHelper.checkPermission(context as Activity)
                        return@StyledIconButton
                    }

                    SpeechHelper.recognizeSpeech(context as Activity) {
                        mainModel.insertedText = it
                        mainModel.enqueueTranslation()
                    }
                }
            }

            if (mainModel.insertedText == "") {

                if(mainModel.extracting){
                    val circcularModifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(30.dp)

                    if (mainModel.extracting) {
                        Box(contentAlignment = Alignment.Center){
                            Text(
                                "${mainModel.extractionProgess}%",
                                textAlign = TextAlign.Center,
                                fontSize = 10.sp
                            )
                            CircularProgressIndicator(
                                modifier = circcularModifier
                            )
                        }

                    }
                }else{
                    StyledIconButton(
                        imageVector = Icons.Default.Image
                    ) {
                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            TessHelper.checkPermission(context as Activity)
                            return@StyledIconButton
                        }
                        cropLauncher.launch(CropImageContractOptions(imageUri, cropImageOptions = CropImageOptions()))
                    }
                }

            }

            var copyImageVector by remember {
                mutableStateOf(Icons.Default.ContentCopy)
            }

            if (mainModel.translation.translatedText != "") {
                StyledIconButton(
                    imageVector = copyImageVector,
                    onClick = {
                        ClipboardHelper(
                            context
                        ).write(
                            mainModel.translation.translatedText
                        )
                        copyImageVector = Icons.Default.DoneAll
                        handler.postDelayed({
                            copyImageVector = Icons.Default.ContentCopy
                        }, 2000)
                    }
                )
            }

            if (mainModel.translation.translatedText != "") {
                StyledIconButton(
                    imageVector = Icons.Default.Share,
                    onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, mainModel.translation.translatedText)
                            type = "text/plain"
                        }

                        val shareIntent = Intent.createChooser(sendIntent, null)
                        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(shareIntent)
                    }
                )
            }

            if (mainModel.insertedText != "") {
                StyledIconButton(
                    imageVector = Icons.Default.Clear,
                    onClick = {
                        mainModel.clearTranslation()
                    }
                )
            }


            TopBarMenu(menuItems)
        }
    )
}
