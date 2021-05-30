# whereToGo
![Problem solving-bro](https://user-images.githubusercontent.com/50915778/119961008-dccf1500-bfa5-11eb-8a4e-09b4389a2eb6.png)

[Link](https://github.com/virtualms/WhereToGo) to the repository where YOLOv5 model training process is explained.

`WhereToGo` is an Android app coinceived to help visually impaired or blind people to explore indoor ambients, like for example apartments, offices, markets and so on. It uses a vocal speech synthesizer to notify the subject of the presence of useful objects inside a room. 

For this version of the project, whe focused upon the two most important objects for these people to move inside a new ambient: doors and handles.

# How does this app work?

The focal point of the app is the class [`ObjectDetectionActivity`](app/src/main/java/org/pytorch/demo/objectdetection/ObjectDetectionActivity.java), whose `analyzeImage` method is responsible for analysing images from the camera.

You can set the policy that regulates image acquisition by changing the `ImageReaderMode` in the class [`AbstractCameraXActivity`](app/src/main/java/org/pytorch/demo/objectdetection/AbstractCameraXActivity.java) (line 104). At the moment there are 2 possibility:

1. `ACQUIRE_LATEST_IMAGE`:    non-blocking behaviour. The frames arriving during image analysis are discarded.
2. `ACQUIRE_NEXT_IMAGE`:      blocking behaviour. The frames arriving during image analysis are stored until the maximum capacity is reached.
    
 # How to change the model?
 
 The YOLO model file is stored in the 
[`assets folder`](app/src/main/assets), where you can find also the [`classes.txt`](app/src/main/assets/classes.txt) file that lists the possible outcomes of the detections.
 Once you have add your YOLOv5 model file, you have to edit the [`config.properties`](app/src/main/res/raw/config.properties) file according your model structure.
 

