import tensorflow as tf
from tensorflow import keras

dir_model_ROM = 'ROM analysis/model/xyz/'

model = tf.keras.layers.TFSMLayer(dir_model_ROM, call_endpoint='serving_default')

h5_model_path = 'model_keras/model_ROM.h5'
model.save(h5_model_path)